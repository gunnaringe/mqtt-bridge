package com.github.gunnaringe.wg2mqtt.mqtt

import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.gunnaringe.wg2mqtt.Events
import com.github.gunnaringe.wg2mqtt.Metrics
import com.github.gunnaringe.wg2mqtt.asString
import com.github.gunnaringe.wg2mqtt.model.Metadata
import com.github.gunnaringe.wg2mqtt.model.SmsEnvelope
import com.github.gunnaringe.wg2mqtt.model.objectMapper
import io.github.davidepianca98.mqtt.broker.interfaces.PacketInterceptor
import io.github.davidepianca98.mqtt.packets.MQTTPacket
import io.github.davidepianca98.mqtt.packets.mqtt.MQTTPublish
import org.slf4j.LoggerFactory
import java.time.Instant

/** Turns messages published by clients into outbox events. */
@OptIn(ExperimentalUnsignedTypes::class)
class MqttMessages : PacketInterceptor {
    override fun packetReceived(clientId: String, username: String?, password: UByteArray?, packet: MQTTPacket) {
        if (packet !is MQTTPublish) return
        // Messages published by the bridge itself have no username
        if (username == null) return

        val payload = packet.payload?.asString() ?: ""
        logger.info("Received packet: topic=${packet.topicName} payload=$payload")

        try {
            handle(username, payload)
        } catch (e: Exception) {
            logger.warn("Invalid message from $username: $payload", e)
            received("invalid")
        }
    }

    private fun handle(username: String, payload: String) {
        val json = objectMapper.readValue<Map<String, Any>>(payload)
        val metadata = Metadata(timestamp = Instant.now(), user = username, type = "")

        when {
            "sms" in json -> {
                val envelope = objectMapper.convertValue<SmsEnvelope>(json)
                // Users may only send from their own number
                if (envelope.sms.from != "+$username") {
                    logger.warn("Rejecting SMS from ${envelope.sms.from} published by $username")
                    received("rejected")
                    return
                }
                Events.outbox.post(envelope.copy(metadata = metadata.copy(type = "sms")))
                received("accepted")
            }

            else -> {
                logger.warn("No known type in message: $payload")
                received("invalid")
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MqttMessages::class.java)

        private fun received(result: String) = Metrics.counter("mqtt.messages.received", "result", result).increment()
    }
}

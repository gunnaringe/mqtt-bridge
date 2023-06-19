package com.github.gunnaringe.wg2mqtt.mqtt

import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.gunnaringe.wg2mqtt.Events
import com.github.gunnaringe.wg2mqtt.asString
import com.github.gunnaringe.wg2mqtt.model.Event
import com.github.gunnaringe.wg2mqtt.model.Metadata
import com.github.gunnaringe.wg2mqtt.model.SmsEnvelope
import com.github.gunnaringe.wg2mqtt.model.objectMapper
import mqtt.broker.interfaces.PacketInterceptor
import mqtt.packets.mqtt.MQTTPublish
import org.slf4j.LoggerFactory
import java.time.Instant

class MqttMessages : PacketInterceptor {
    @OptIn(ExperimentalUnsignedTypes::class)
    override fun packetReceived(
        clientId: String,
        username: String?,
        password: UByteArray?,
        packet: mqtt.packets.MQTTPacket,
    ) {
        if (packet is MQTTPublish) {
            packet.topicName
            packet.payload
            packet.timestamp
            val payload = packet.payload?.asString() ?: ""
            logger.info("Received packet: topic=${packet.topicName} payload=$payload")
            if (username == null) {
                logger.warn("No username for packet: $packet")
                return
            }

            val json = objectMapper.readValue<Map<String, Any>>(payload)
            val type = json.keys.firstOrNull { it in listOf("sms") }
            if (type == null) {
                logger.warn("No type for message: $payload")
                return
            }
            val metadata = Metadata(
                timestamp = Instant.now(), // packet.timestamp,
                user = username,
                type = type,
            )

            val event = when (type) {
                "sms" -> convert<SmsEnvelope>(json).copy(metadata = metadata)
                else -> null
            }

            if (event != null) {
                Events.outbox.post(event)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MqttMessages::class.java)

        inline fun <reified T : Event> convert(json: Map<String, Any>): T = objectMapper.convertValue<T>(json)
    }
}

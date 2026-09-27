package com.github.gunnaringe.wg2mqtt.mqtt

import com.github.gunnaringe.wg2mqtt.Metrics
import io.github.davidepianca98.mqtt.broker.Broker
import io.github.davidepianca98.mqtt.broker.interfaces.BytesMetrics
import io.github.davidepianca98.mqtt.packets.Qos
import io.github.davidepianca98.mqtt.packets.mqttv5.MQTT5Properties
import org.slf4j.LoggerFactory

class MqttServer(wsPort: Int, mqttPort: Int, auth: MqttAuthenticator, messageHandler: MqttMessages) {
    private val broker = Broker(
        enhancedAuthenticationProviders = mapOf(),
        authentication = auth,
        authorization = auth,
        packetInterceptor = messageHandler,
        bytesMetrics = TrafficMetrics,
        webSocketPort = wsPort,
        port = mqttPort,
    )

    init {
        // Checked against the broker rather than tracked via onDisconnect, which also
        // fires for the old session when a client reconnects with the same client ID
        Metrics.registry.gauge("mqtt.clients.connected", auth) { a ->
            a.clientIds.removeIf { !broker.isClientConnected(it) }
            a.clientIds.size.toDouble()
        }
    }

    fun start() {
        logger.info("Running blocking MQTT broker: mqtt=${broker.port} ws=${broker.webSocketPort}")
        broker.listen()
    }

    fun stop() = broker.stop()

    @OptIn(ExperimentalUnsignedTypes::class)
    fun send(topic: String, payload: String) {
        broker.publish(
            retain = false,
            topicName = topic,
            qos = Qos.EXACTLY_ONCE,
            properties = MQTT5Properties(),
            payload = payload.toByteArray().toUByteArray(),
        )
        published.increment()
    }

    private object TrafficMetrics : BytesMetrics {
        private val received = Metrics.counter("mqtt.bytes.received")
        private val sent = Metrics.counter("mqtt.bytes.sent")

        override fun received(clientId: String, bytes: Long) = received.increment(bytes.toDouble())
        override fun sent(clientId: String, bytes: Long) = sent.increment(bytes.toDouble())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MqttServer::class.java)
        private val published = Metrics.counter("mqtt.messages.published")
    }
}

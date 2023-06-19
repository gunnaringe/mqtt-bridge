package com.github.gunnaringe.wg2mqtt.mqtt

import mqtt.broker.Broker
import mqtt.packets.Qos
import org.slf4j.LoggerFactory

class MqttServer(wsPort: Int, auth: MqttAuthenticator, messageHandler: MqttMessages) {
     val broker = Broker(
        enhancedAuthenticationProviders = mapOf(),
        authentication = auth,
        authorization = auth,
        packetInterceptor = messageHandler,
        webSocketPort = wsPort,
        port = 0,
    )

    fun start() {
        logger.info("Running blocking MQTT broker: wsPort=${broker.webSocketPort}")
        broker.listen()
    }

    fun stop() = broker.stop()

    @OptIn(ExperimentalUnsignedTypes::class)
    fun send(topic: String, payload: String) = broker.publish(
        retain = false,
        topicName = topic,
        qos = Qos.EXACTLY_ONCE,
        properties = null,
        payload = payload.toByteArray().toUByteArray(),
    )

    companion object {
        private val logger = LoggerFactory.getLogger(MqttServer::class.java)
    }
}

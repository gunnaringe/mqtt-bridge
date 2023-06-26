package com.github.gunnaringe.wg2mqtt.mqtt

import mqtt.broker.Broker
import mqtt.packets.Qos
import mqtt.packets.mqttv5.MQTT5Properties
import org.slf4j.LoggerFactory

class MqttServer(wsPort: Int, mqttPort: Int, auth: MqttAuthenticator, messageHandler: MqttMessages) {
    private val broker = Broker(
        enhancedAuthenticationProviders = mapOf(),
        authentication = auth,
        authorization = auth,
        packetInterceptor = messageHandler,
        webSocketPort = wsPort,
        port = mqttPort,
    )

    fun start() {
        logger.info("Running blocking MQTT broker: mqtt=${broker.port} ws=${broker.webSocketPort}")
        broker.listen()
    }

    fun stop() = broker.stop()

    @OptIn(ExperimentalUnsignedTypes::class)
    fun send(topic: String, payload: String) = broker.publish(
        retain = false,
        topicName = topic,
        qos = Qos.EXACTLY_ONCE,
        properties = MQTT5Properties(),
        payload = payload.toByteArray().toUByteArray(),
    )

    companion object {
        private val logger = LoggerFactory.getLogger(MqttServer::class.java)
    }
}

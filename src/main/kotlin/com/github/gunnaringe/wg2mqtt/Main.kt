package com.github.gunnaringe.wg2mqtt

import com.github.gunnaringe.wg2mqtt.model.asJson
import com.github.gunnaringe.wg2mqtt.mqtt.MqttAuthenticator
import com.github.gunnaringe.wg2mqtt.mqtt.MqttMessages
import com.github.gunnaringe.wg2mqtt.mqtt.MqttServer
import com.github.gunnaringe.wg2mqtt.users.Database
import com.github.gunnaringe.wg2mqtt.users.UserRegistration
import com.github.gunnaringe.wg2mqtt.wg2.ConsentListener
import com.github.gunnaringe.wg2mqtt.wg2.EventsV0Listener
import com.github.gunnaringe.wg2mqtt.wg2.OnMessageFromWg2
import com.github.gunnaringe.wg2mqtt.wg2.SmsSender
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addFileSource
import com.wgtwo.auth.WgtwoAuth
import io.grpc.ManagedChannelBuilder
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

private val logger = LoggerFactory.getLogger("com.github.gunnaringe.smschatbot.Main")
private val scope = setOf(
    "events.sms.subscribe",
    "events.voice.subscribe",
    "mms.send.from_subscriber",
    "sms.text:send_from_subscriber",
    "sms.text:send_to_subscriber",

)

fun main(args: Array<String>) {
    val config = ConfigLoaderBuilder.default()
        .addDefaultPreprocessors()
        .addEnvironmentSource(useUnderscoresAsSeparator = true, allowUppercaseNames = true)
        .apply { args.forEach { file -> addFileSource(file) } }
        .build()
        .loadConfigOrThrow<Config>()

    Database.connect(config.sqlite.path)

    val wgtwoAuth = WgtwoAuth.builder(config.wg2.clientId, config.wg2.clientSecret.value).build()
    val tokenSource = wgtwoAuth.clientCredentials.newTokenSource(scope.joinToString(separator = " "))

    val channel = ManagedChannelBuilder.forTarget("api.wgtwo.com:443")
        .useTransportSecurity()
        .keepAliveTime(30, TimeUnit.SECONDS)
        .keepAliveTimeout(10, TimeUnit.SECONDS)
        .keepAliveWithoutCalls(true)
        .build()

    // Handle user registration on consent added
    UserRegistration

    // Start listening to events from WG2
    val consentListener = ConsentListener(channel, tokenSource, config.wg2.eventQueue).also {
        it.start()
    }
    val eventsV0Listener = EventsV0Listener(channel, tokenSource, config.wg2.eventQueue).also {
        it.start()
    }

    // Start handlers that sends to WG2
    SmsSender(channel, tokenSource).subscribe()

    // Start MQTT server
    val mqttAuth = MqttAuthenticator()
    val mqttMessageHandler = MqttMessages()
    val mqttServer = MqttServer(
        wsPort = config.mqtt.ports.ws,
        mqttPort = config.mqtt.ports.mqtt,
        auth = mqttAuth,
        messageHandler = mqttMessageHandler,
    )

    OnMessageFromWg2 { message ->
        logger.info("Sending to MQTT: ${message.asJson()}")
        mqttServer.send(message.createTopic("inbox"), message.asJson())
    }

    atShutdown {
        logger.info("Shutting down...")
        consentListener.close()
        eventsV0Listener.close()
        mqttServer.stop()
        channel.shutdown()
        channel.awaitTermination(10, TimeUnit.SECONDS)
        Events.close()
        Thread.sleep(10_000)
    }

    mqttServer.start()
}

private fun atShutdown(function: () -> Unit) = Runtime.getRuntime().addShutdownHook(Thread(function))

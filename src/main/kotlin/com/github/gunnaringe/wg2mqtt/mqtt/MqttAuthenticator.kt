package com.github.gunnaringe.wg2mqtt.mqtt

import com.github.gunnaringe.wg2mqtt.Metrics
import com.github.gunnaringe.wg2mqtt.asString
import com.github.gunnaringe.wg2mqtt.users.User
import io.github.davidepianca98.mqtt.broker.interfaces.Authentication
import io.github.davidepianca98.mqtt.broker.interfaces.Authorization
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/** Users authenticate with their username/password, and may only use topics under `{username}/`. */
@OptIn(ExperimentalUnsignedTypes::class)
class MqttAuthenticator : Authentication, Authorization {
    /** Client IDs that have authenticated; [MqttServer] prunes the ones no longer connected. */
    val clientIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun authenticate(clientId: String, username: String?, password: UByteArray?): Boolean {
        val passwordString = password?.asString()
        if (username.isNullOrEmpty() || passwordString.isNullOrEmpty()) {
            logger.warn("Authentication failed: username=$username")
            authFailure.increment()
            return false
        }
        val authenticated = User.getAndAuthenticate(username, passwordString) != null
        if (authenticated) {
            authSuccess.increment()
            clientIds.add(clientId)
        } else {
            authFailure.increment()
        }
        return authenticated
    }

    override fun authorize(
        clientId: String,
        username: String?,
        password: UByteArray?,
        topicName: String,
        isSubscription: Boolean,
        payload: UByteArray?,
    ): Boolean {
        val validTopic = username != null && topicName.startsWith("$username/")
        if (!validTopic) {
            logger.warn("Authorization of user $username for topic $topicName: failed")
            authzDenied.increment()
        }
        return validTopic
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MqttAuthenticator::class.java)
        private val authSuccess = Metrics.counter("mqtt.authentications", "result", "success")
        private val authFailure = Metrics.counter("mqtt.authentications", "result", "failure")
        private val authzDenied = Metrics.counter("mqtt.authorizations.denied")
    }
}

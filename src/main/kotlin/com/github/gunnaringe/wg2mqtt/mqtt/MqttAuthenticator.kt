package com.github.gunnaringe.wg2mqtt.mqtt

import com.github.gunnaringe.wg2mqtt.asString
import com.github.gunnaringe.wg2mqtt.users.User
import io.github.davidepianca98.mqtt.broker.interfaces.Authentication
import io.github.davidepianca98.mqtt.broker.interfaces.Authorization
import org.slf4j.LoggerFactory

/** Users authenticate with their username/password, and may only use topics under `{username}/`. */
@OptIn(ExperimentalUnsignedTypes::class)
class MqttAuthenticator : Authentication, Authorization {

    override fun authenticate(clientId: String, username: String?, password: UByteArray?): Boolean {
        val passwordString = password?.asString()
        if (username.isNullOrEmpty() || passwordString.isNullOrEmpty()) {
            logger.warn("Authentication failed: username=$username")
            return false
        }
        return User.getAndAuthenticate(username, passwordString) != null
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
        }
        return validTopic
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MqttAuthenticator::class.java)
    }
}

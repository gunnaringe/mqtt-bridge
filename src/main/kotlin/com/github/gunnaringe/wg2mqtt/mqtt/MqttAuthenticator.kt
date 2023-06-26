package com.github.gunnaringe.wg2mqtt.mqtt

import com.github.gunnaringe.wg2mqtt.asString
import com.github.gunnaringe.wg2mqtt.users.User
import mqtt.broker.interfaces.Authentication
import mqtt.broker.interfaces.Authorization
import org.slf4j.LoggerFactory

@OptIn(ExperimentalUnsignedTypes::class)
class MqttAuthenticator : Authentication, Authorization {

    /** Add some rate limiting here? */
    override fun authenticate(clientId: String, username: String?, password: UByteArray?): Boolean {
        val passwordString = password?.asString()
        if (username.isNullOrEmpty() || passwordString.isNullOrEmpty()) {
            logger.warn("Authentication failed: username=$username")
            return false
        }

        val user = User.getAndAuthenticate(username, passwordString)
        return user != null
    }

    override fun authorize(
        clientId: String,
        username: String?,
        password: UByteArray?,
        topicName: String,
        isSubscription: Boolean,
        payload: UByteArray?,
    ): Boolean {
        val validTopic = topicName.startsWith("$username/")
        if (!validTopic) {
            logger.warn("Authorization of user $username for topic $topicName: failed")
        }
        return validTopic
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MqttAuthenticator::class.java)
    }
}

package com.github.gunnaringe.wg2mqtt.mqtt

import com.github.gunnaringe.wg2mqtt.Users
import com.github.gunnaringe.wg2mqtt.asString
import mqtt.broker.interfaces.Authentication
import mqtt.broker.interfaces.Authorization
import org.slf4j.LoggerFactory

@OptIn(ExperimentalUnsignedTypes::class)
class MqttAuthenticator(private val users: Users) : Authentication, Authorization {

    /** Add some rate limiting here? */
    override fun authenticate(clientId: String, username: String?, passwordBytes: UByteArray?): Boolean {
        val password = passwordBytes?.asString()
        if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
            logger.warn("Authentication failed: username=$username")
            return false
        }

        val isMatchingPassword = users.check(username, password)
        if (!isMatchingPassword) {
            logger.warn("Authentication failed: $username")
        }
        return isMatchingPassword
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

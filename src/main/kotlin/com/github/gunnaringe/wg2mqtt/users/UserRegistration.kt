package com.github.gunnaringe.wg2mqtt.users

import com.github.gunnaringe.wg2mqtt.Events
import com.github.gunnaringe.wg2mqtt.model.ConsentEnvelope
import com.github.gunnaringe.wg2mqtt.model.Metadata
import com.github.gunnaringe.wg2mqtt.model.Sms
import com.github.gunnaringe.wg2mqtt.model.SmsEnvelope
import com.google.common.eventbus.Subscribe
import org.slf4j.LoggerFactory

/** Creates users when consent is added (sending them their password by SMS), and removes them on revoke. */
class UserRegistration {
    init {
        Events.inbox.register(this)
    }

    @Subscribe
    fun onNewConsent(envelope: ConsentEnvelope) {
        val metadata = envelope.metadata ?: return

        when (envelope.consent.action) {
            "added" -> addUser(metadata)
            "revoked" -> removeUser(metadata.user)
            else -> logger.info("Unhandled consent action: ${envelope.consent.action}")
        }
    }

    private fun removeUser(user: String) {
        val deleted = User.delete(user)
        logger.info("Removed user: $user (existed=$deleted)")
    }

    private fun addUser(metadata: Metadata) {
        val user = metadata.user
        logger.info("New user: $user")
        val password = User.create(user)

        val sms = SmsEnvelope(
            metadata = metadata.copy(type = "sms"),
            sms = Sms(
                from = "+$user",
                to = "+$user",
                content = """
                    Welcome to MQTT Bridge!

                    MQTT Bridge has been added to your subscription.
                    This allows you to listen for SMS and other events,
                    and send SMSes from your number, as done by this message.

                    Connect to the MQTT broker using:

                    Username: $user
                    Password: $password
                    Server: mqtts://mqtt-bridge.haxxor.xyz:8883

                    Subscribe to topic: $user/inbox/#

                    For more details, see
                    https://github.com/gunnaringe/mqtt-bridge
                """.trimIndent(),
            ),
        )
        Events.outbox.post(sms)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserRegistration::class.java)
    }
}

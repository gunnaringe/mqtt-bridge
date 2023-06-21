package com.github.gunnaringe.wg2mqtt

import com.github.gunnaringe.wg2mqtt.model.ConsentEnvelope
import com.github.gunnaringe.wg2mqtt.model.Metadata
import com.github.gunnaringe.wg2mqtt.model.Sms
import com.github.gunnaringe.wg2mqtt.model.SmsEnvelope
import com.google.common.eventbus.Subscribe
import org.slf4j.LoggerFactory

class Users(users: List<User>) {
    private val usernameAndPassword = users.associate { it.phone to it.password.value }

    init {
        Events.inbox.register(this)
    }

    fun check(username: String, password: String) =
        username.isNotEmpty() && password.isNotEmpty() && usernameAndPassword[username] == password

    @Subscribe
    fun onNewConsent(envelope: ConsentEnvelope) {
        val consent = envelope.consent
        if (consent.action != "added") {
            return
        }

        val id = consent.subscription
        val user = envelope.metadata?.user ?: return

        val sms = SmsEnvelope(
            metadata = Metadata(
                timestamp = envelope.metadata.timestamp,
                user = envelope.metadata.user,
                type = "sms",
            ),
            sms = Sms(
                from = "MQTT Bridge",
                to = "+$user",
                content = """
                    Welcome to MQTT Bridge!
                    This should really have sent you a link with info.
                    
                    https://mqtt-bridge.haxxor.xyz/
                """.trimIndent(),
            ),
        )
        Events.outbox.post(sms)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Users::class.java)
    }
}

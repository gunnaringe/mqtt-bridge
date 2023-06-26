package com.github.gunnaringe.wg2mqtt.users

import com.github.gunnaringe.wg2mqtt.Events
import com.github.gunnaringe.wg2mqtt.model.ConsentEnvelope
import com.github.gunnaringe.wg2mqtt.model.Metadata
import com.github.gunnaringe.wg2mqtt.model.Sms
import com.github.gunnaringe.wg2mqtt.model.SmsEnvelope
import com.google.common.eventbus.Subscribe
import org.jetbrains.exposed.sql.Slf4jSqlDebugLogger
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

object UserRegistration {
    private val logger = LoggerFactory.getLogger(UserRegistration::class.java)

    init {
        Events.inbox.register(this)
    }

    @Subscribe
    fun onNewConsent(envelope: ConsentEnvelope) {
        val consent = envelope.consent
        val user = envelope.metadata?.user ?: return

        when (consent.action) {
            "added" -> addUser(user, envelope)
            "revoked" -> removeUser(user, envelope)
            else -> logger.info("Unknown consent action: ${consent.action}")
        }
    }

    private fun removeUser(user: String, envelope: ConsentEnvelope) {
        logger.info("Removed user: $user")
        val rows = transaction { Users.deleteWhere { id eq user } }
        logger.info("Deleted $rows rows")
    }

    private fun addUser(user: String, envelope: ConsentEnvelope) {
        logger.info("New user: $user")
        val password = transaction {
            addLogger(Slf4jSqlDebugLogger)
            User.create(user)
        }

        val sms = SmsEnvelope(
            metadata = Metadata(
                timestamp = envelope.metadata!!.timestamp,
                user = envelope.metadata!!.user,
                type = "sms",
            ),
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
                    Server: mqtts://mqtt.haxxor.xyz
                    
                    Subscribe to topic: +$user/inbox/#
                    
                    For more details, see
                    https://github.com/gunnaringe/wg2-mqtt
                """.trimIndent(),
            ),
        )
        Events.outbox.post(sms)
    }
}

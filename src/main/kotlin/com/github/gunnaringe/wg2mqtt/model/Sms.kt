package com.github.gunnaringe.wg2mqtt.model

data class SmsEnvelope(
    override val metadata: Metadata?,
    val sms: Sms,
) : Event

data class Sms(
    val from: String,
    val to: String,
    val content: String,
)

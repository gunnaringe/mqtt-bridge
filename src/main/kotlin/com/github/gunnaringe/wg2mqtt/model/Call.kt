package com.github.gunnaringe.wg2mqtt.model

data class CallEnvelope(
    override val metadata: Metadata?,
    val call: Call,
) : Event

data class Call(
    val from: String,
    val to: String,
    val action: String,
    val hiddenCaller: Boolean,
)

package com.github.gunnaringe.wg2mqtt.model

data class MmsEnvelope(
    override val metadata: Metadata?,
    val mms: Mms,
) : Event

data class Mms(
    val from: String,
    val to: String,
    val opus: String?,
)

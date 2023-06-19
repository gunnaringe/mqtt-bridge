package com.github.gunnaringe.wg2mqtt.model

data class ConsentEnvelope(
    override val metadata: Metadata?,
    val consent: Consent,
) : Event

data class Consent(
    val subscription: String,
    val action: String,
    val scopes: List<String>,
)

package com.github.gunnaringe.wg2mqtt.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant

val objectMapper: ObjectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

interface Event {
    val metadata: Metadata?
    fun createTopic(box: String) = "${metadata?.user}/$box/${metadata?.type}"
}

fun Event.asJson(): String = objectMapper.writeValueAsString(this)

data class Metadata(
    val timestamp: Instant,
    @JsonIgnore
    val user: String,
    @JsonIgnore
    val type: String, // Make this an enum? E.g. "sms"
)

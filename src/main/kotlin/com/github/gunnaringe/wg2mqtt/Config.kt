package com.github.gunnaringe.wg2mqtt

import com.sksamuel.hoplite.Masked

data class Config(
    val wg2: Wg2Config,
    val mqtt: MqttConfig,
    val sqlite: SqliteConfig,
)

data class SqliteConfig(
    val path: String,
)

data class Wg2Config(
    val clientId: String,
    val clientSecret: Masked,
    val eventQueue: String?,
    val apiTarget: String = "api.shamrock.wgtwo.com:443",
)

data class MqttConfig(
    val ports: MqttPortConfig,
)

data class MqttPortConfig(
    val ws: Int,
    val mqtt: Int,
)

package com.github.gunnaringe.wg2mqtt

import com.sksamuel.hoplite.Masked

data class Config(
    val wg2: Wg2Config,
    val mqtt: MqttConfig,
    val users: List<User>,
    val sqlite: SqliteConfig,
)

data class SqliteConfig(
    val path: String,
)

data class Wg2Config(
    val clientId: String,
    val clientSecret: Masked,
    val eventQueue: String?,
)

data class MqttConfig(
    val ports: MqttPortConfig,
)

data class MqttPortConfig(
    val ws: Int,
    val mqtt: Int,
)

data class User(
    val phone: String,
    val password: Masked,
)

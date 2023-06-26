package com.github.gunnaringe.wg2mqtt.users

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import kotlin.random.Random

object Passwords {
    private val alphabet = ('a'..'z') + ('A'..'Z') + ('0'..'9')

    fun randomPassword(length: Int) = (1..length)
        .map { Random.nextInt(0, alphabet.size) }
        .map(alphabet::get)
        .joinToString("")

    fun randomSalt(bytes: Int) = ByteArray(bytes).apply {
        SecureRandom().nextBytes(this)
    }

    fun hash(salt: ByteArray, password: String): ByteArray {
        val argon2Parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withParallelism(2)
            .withSalt(salt)
            .build()

        val generate = Argon2BytesGenerator().apply {
            init(argon2Parameters)
        }

        val result = ByteArray(32)
        generate.generateBytes(password.toByteArray(), result, 0, result.size)
        return result
    }
}

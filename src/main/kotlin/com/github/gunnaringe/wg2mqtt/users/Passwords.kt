package com.github.gunnaringe.wg2mqtt.users

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom

object Passwords {
    private val alphabet = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    private val random = SecureRandom()

    fun randomPassword(length: Int = 12) = (1..length)
        .map { alphabet[random.nextInt(alphabet.size)] }
        .joinToString("")

    fun randomSalt(bytes: Int = 16) = ByteArray(bytes).apply(random::nextBytes)

    /**
     * Argon2id. The parameters are pinned to what BouncyCastle defaulted to when the
     * existing hashes were created — changing any of them invalidates every stored password.
     */
    fun hash(salt: ByteArray, password: String): ByteArray {
        val argon2Parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(3)
            .withMemoryAsKB(4096)
            .withParallelism(2)
            .withSalt(salt)
            .build()

        val generator = Argon2BytesGenerator().apply { init(argon2Parameters) }
        val result = ByteArray(32)
        generator.generateBytes(password.toByteArray(), result, 0, result.size)
        return result
    }

    fun matches(salt: ByteArray, expectedHash: ByteArray, password: String): Boolean =
        MessageDigest.isEqual(expectedHash, hash(salt, password))
}

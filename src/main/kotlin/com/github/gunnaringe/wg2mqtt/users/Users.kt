package com.github.gunnaringe.wg2mqtt.users

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant
import org.jetbrains.exposed.v1.jdbc.Database as ExposedDatabase

object Database {
    fun connect(filename: String) {
        ExposedDatabase.connect("jdbc:sqlite:$filename", "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(Users)
        }
    }
}

object Users : IdTable<String>() {
    override val id: Column<EntityID<String>> = text("username").entityId()
    override val primaryKey = PrimaryKey(id)
    val salt = binary("salt")
    val password = binary("password")
    val phone = text("phone").uniqueIndex()
    val created = timestamp("created").defaultExpression(CurrentTimestamp)
    val lastLogin = timestamp("last_login").nullable()
}

class User(id: EntityID<String>) : Entity<String>(id) {
    var username by Users.id
    var phone by Users.phone
    private var salt by Users.salt
    private var password by Users.password
    var created by Users.created
    var lastLogin by Users.lastLogin

    private fun matches(providedPassword: String): Boolean =
        Passwords.matches(salt, password, providedPassword)

    private fun updatePassword(newPassword: String) {
        val newSalt = Passwords.randomSalt()
        salt = newSalt
        password = Passwords.hash(newSalt, newPassword)
    }

    companion object : EntityClass<String, User>(Users) {
        private val logger = LoggerFactory.getLogger(User::class.java)

        fun getAndAuthenticate(username: String, password: String): User? = transaction {
            val user = findById(username)
            when {
                user == null -> {
                    logger.info("User not found: $username")
                    null
                }

                user.matches(password) -> {
                    logger.info("User authenticated: $username")
                    user.lastLogin = Instant.now()
                    user
                }

                else -> {
                    logger.info("User not authenticated: $username")
                    null
                }
            }
        }

        fun setPassword(username: String, password: String): Boolean = transaction {
            val user = findById(username) ?: return@transaction false
            user.updatePassword(password)
            true
        }

        /** Creates a user with a random password, and returns that password. */
        fun create(username: String): String = transaction {
            val password = Passwords.randomPassword()
            User.new(username) {
                this.phone = "+$username"
                this.updatePassword(password)
            }
            password
        }

        fun delete(username: String): Boolean = transaction {
            val user = findById(username) ?: return@transaction false
            user.delete()
            true
        }
    }
}

package com.github.gunnaringe.wg2mqtt.users

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Slf4jSqlDebugLogger
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant

object Database {
    fun connect(filename: String) {
        Database.connect("jdbc:sqlite:$filename", "org.sqlite.JDBC")
        transaction {
            addLogger(StdOutSqlLogger)
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
    val created = timestamp("created").defaultExpression(CurrentTimestamp())
    val lastLogin = timestamp("last_login").nullable()
}

class User(id: EntityID<String>) : Entity<String>(id) {
    var username by Users.id
    var phone by Users.phone
    private var salt by Users.salt
    var password by Users.password
    var created by Users.created
    var lastLogin by Users.lastLogin

    fun matches(providedPassword: String): Boolean = transaction {
        addLogger(Slf4jSqlDebugLogger)
        val hash = Passwords.hash(this@User.salt, providedPassword)
        this@User.password.contentEquals(hash)
    }

    fun updateLastLogin() {
        transaction {
            addLogger(Slf4jSqlDebugLogger)
            lastLogin = Instant.now()
        }
    }

    companion object : EntityClass<String, User>(Users) {
        private val logger = LoggerFactory.getLogger(User::class.java)

        fun getAndAuthenticate(username: String, password: String): User? = transaction {
            addLogger(Slf4jSqlDebugLogger)
            val user = findById(username)
            when {
                user == null -> {
                    logger.info("User not found: $username")
                    null
                }

                user.matches(password) -> {
                    user.also {
                        logger.info("User authenticated: $username")
                        it.updateLastLogin()
                    }
                }

                else -> {
                    logger.info("User not authenticated: $username")
                    null
                }
            }
        }

        fun create(username: String): String {
            val salt = Passwords.randomSalt(16)
            val password = Passwords.randomPassword(12)
            val cipherText = Passwords.hash(salt, password)

            User.new(username) {
                this.phone = "+$username"
                this.salt = salt
                this.password = cipherText
            }

            return password
        }
    }
}

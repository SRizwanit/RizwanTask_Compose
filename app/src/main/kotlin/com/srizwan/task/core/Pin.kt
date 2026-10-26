package com.srizwan.task.core

import java.security.MessageDigest
import java.security.SecureRandom

/** PIN hashing: sha256("salt:pin") hex — identical to the React app / old Kotlin app, so existing users keep working. */
object Pin {
    fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun newSalt(): String =
        ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    fun hashPin(pin: String, salt: String): String = sha256("$salt:$pin")

    fun verify(user: AppUser, pin: String): Boolean {
        val salt = user.passwordSalt
        val hash = user.passwordHash
        if (salt.isNullOrEmpty() || hash.isNullOrEmpty()) return false
        return hashPin(pin, salt) == hash
    }

    fun hasPin(u: AppUser): Boolean = !u.passwordHash.isNullOrEmpty() && !u.passwordSalt.isNullOrEmpty()
}

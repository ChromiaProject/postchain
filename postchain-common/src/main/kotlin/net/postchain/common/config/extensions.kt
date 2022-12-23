package net.postchain.common.config

import net.postchain.common.reflection.newInstanceOf
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.Secp256K1CryptoSystem
import org.apache.commons.configuration2.Configuration

fun Configuration.cryptoSystem() = getEnvOrStringProperty("POSTCHAIN_CRYPTO_SYSTEM", "crypto")?.let { newInstanceOf<CryptoSystem>(it) }
        ?: Secp256K1CryptoSystem()

fun Configuration.getEnvOrStringProperty(env: String, property: String): String? =
        System.getenv(env) ?: getString(property)

fun Configuration.getEnvOrStringProperty(env: String, property: String, default: String): String =
        System.getenv(env) ?: getString(property, default)

fun Configuration.getEnvOrListProperty(env: String, property: String, default: List<String>): List<String> =
        System.getenv(env)?.split(",") ?: getList(property, default).flatMap { (it as String).split(",") }

fun Configuration.getEnvOrIntProperty(env: String, property: String, default: Int): Int =
        System.getenv(env)?.toInt() ?: getInt(property, default)

fun Configuration.getEnvOrLongProperty(env: String, property: String, default: Long): Long =
        System.getenv(env)?.toLong() ?: getLong(property, default)

fun Configuration.getEnvOrBooleanProperty(env: String, property: String, default: Boolean): Boolean =
        System.getenv(env)?.let { it.toBoolean() } ?: getBoolean(property, default)

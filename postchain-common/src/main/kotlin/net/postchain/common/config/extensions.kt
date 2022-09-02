package net.postchain.common.config

import net.postchain.common.reflection.newInstanceOf
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.Secp256K1CryptoSystem
import org.apache.commons.configuration2.Configuration

fun Configuration.cryptoSystem() = getEnvOrStringProperty("POSTCHAIN_CRYPTO_SYSTEM", "crypto")?.let { newInstanceOf<CryptoSystem>(it) }
    ?: Secp256K1CryptoSystem()

fun Configuration.getEnvOrStringProperty(env: String, property: String): String? =
    System.getProperty(env) ?: getString(property)

fun Configuration.getEnvOrStringProperty(env: String, property: String, default: String): String =
    System.getProperty(env) ?: getString(property, default)

fun Configuration.getEnvOrIntProperty(env: String, property: String, default: Int): Int =
    System.getProperty(env)?.toInt() ?: getInt(property, default)

fun Configuration.getEnvOrLongProperty(env: String, property: String, default: Long): Long =
    System.getProperty(env)?.toLong() ?: getLong(property, default)

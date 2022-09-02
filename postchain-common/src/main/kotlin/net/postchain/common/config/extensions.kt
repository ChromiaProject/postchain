package net.postchain.common.config

import net.postchain.common.reflection.newInstanceOf
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.Secp256K1CryptoSystem
import org.apache.commons.configuration2.Configuration

fun Configuration.cryptoSystem() = getEnvOrStringProperty("POSTCHAIN_CRYPTO_SYSTEM", "crypto")?.let { newInstanceOf<CryptoSystem>(it) }
    ?: Secp256K1CryptoSystem()

private fun Configuration.getEnvOrStringProperty(env: String, property: String) =
    System.getProperty(env) ?: getString(property)
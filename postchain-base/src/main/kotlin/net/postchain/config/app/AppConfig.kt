// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.app

import net.postchain.common.config.Config
import net.postchain.common.hexStringToByteArray
import net.postchain.common.reflection.newInstanceOf
import net.postchain.core.Infrastructure
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.Secp256K1CryptoSystem
import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.ConfigurationUtils
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler
import java.io.FileWriter
import java.io.PrintWriter

/**
 * Wrapper to the generic [Configuration]
 * Adding some convenience fields, for example regarding database connection.
 */
class AppConfig(private val config: Configuration, val debug: Boolean = false) : Config {

    companion object {

        const val DEFAULT_PORT: Int = 9870

        fun fromPropertiesFile(configFile: String, debug: Boolean = false): AppConfig {
            val params = Parameters().properties()
                    .setFileName(configFile)
                    .setListDelimiterHandler(DefaultListDelimiterHandler(','))

            val configuration = FileBasedConfigurationBuilder(PropertiesConfiguration::class.java)
                    .configure(params)
                    .configuration

            return AppConfig(configuration, debug)
        }

        fun toPropertiesFile(config: Configuration, configFile: String) {
            ConfigurationUtils.dump(config, PrintWriter(FileWriter(configFile)))
        }

        fun removeProperty(config: Configuration, prefix: String) {
            val keys = config.getKeys(prefix).asSequence().toList()
            keys.forEach(config::clearProperty)
        }
    }

    /**
     * Configuration provider
     */
    val nodeConfigProvider: String
        // properties | manual | managed
        get() = config.getString("configuration.provider.node", "properties")

    /**
     * Database
     */
    val databaseDriverclass: String
        get() = config.getString("database.driverclass", "")

    val databaseUrl: String
        get() = System.getenv("POSTCHAIN_DB_URL")
                ?: config.getString("database.url", "")

    val databaseSchema: String
        get() = System.getenv("POSTCHAIN_DB_SCHEMA")
                ?: config.getString("database.schema", "public")

    val databaseUsername: String
        get() = System.getenv("POSTCHAIN_DB_USERNAME")
                ?: config.getString("database.username", "")

    val databasePassword: String
        get() = System.getenv("POSTCHAIN_DB_PASSWORD")
                ?: config.getString("database.password", "")

    val databaseReadConcurrency: Int
        get() = config.getInt("database.readConcurrency", 10)

    val infrastructure: String
        // "base/ebft" is the default
        get() = config.getString("infrastructure", Infrastructure.Ebft.get())

    val cryptoSystem: CryptoSystem
        = newInstanceOf(config.getString("cryptosystem", Secp256K1CryptoSystem::class.qualifiedName))

    /**
     * Pub/Priv keys
     */
    val privKey: String
        get() = System.getenv("POSTCHAIN_PRIVKEY")
                ?: config.getString("messaging.privkey", "")

    val privKeyByteArray: ByteArray
        get() = privKey.hexStringToByteArray()

    val pubKey: String
        get() = System.getenv("POSTCHAIN_PUBKEY")
                ?: config.getString("messaging.pubkey", "")

    val port: Int
        get() = System.getenv("POSTCHAIN_PORT")?.toInt()
                ?: config.getInt("messaging.port", DEFAULT_PORT)

    val pubKeyByteArray: ByteArray
        get() = pubKey.hexStringToByteArray()

    /**
     * Wrappers for [Configuration] getters and other functionalities
     */
    fun getBoolean(key: String, defaultValue: Boolean = false) = config.getBoolean(key, defaultValue)
    fun getLong(key: String, defaultValue: Long = 0) = config.getLong(key, defaultValue)
    fun getInt(key: String, defaultValue: Int = 0) = config.getInt(key, defaultValue)
    fun getString(key: String, defaultValue: String = ""): String = config.getString(key, defaultValue)
    fun getStringArray(key: String): Array<String> = config.getStringArray(key)
    fun subset(prefix: String): Configuration = config.subset(prefix)
    fun getProperty(key: String): Any = config.getProperty(key)
    fun getKeys(prefix: String): MutableIterator<String> = config.getKeys(prefix)
    fun containsKey(key: String) = config.containsKey(key)

    fun cloneConfiguration(): Configuration = ConfigurationUtils.cloneConfiguration(config)
}
// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.app

import net.postchain.base.PeerInfo
import net.postchain.common.config.Config
import net.postchain.common.config.getEnvOrBooleanProperty
import net.postchain.common.config.getEnvOrIntProperty
import net.postchain.common.config.getEnvOrLongProperty
import net.postchain.common.config.getEnvOrStringProperty
import net.postchain.common.hexStringToByteArray
import net.postchain.common.reflection.newInstanceOf
import net.postchain.core.Infrastructure
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.Secp256K1CryptoSystem
import org.apache.commons.configuration2.BaseConfiguration
import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler
import java.io.File
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Wrapper to the generic [Configuration]
 * Adding some convenience fields, for example regarding database connection.
 */
class AppConfig(private val config: Configuration, val debug: Boolean = false) : Config {

    companion object {

        const val DEFAULT_PORT: Int = 9870
        const val DEFAULT_APPLIED_CONFIG_SEND_INTERVAL_MS: Long = 1_000

        @Deprecated(message = "Use fromPropertiesFile(File, Boolean) instead",
                replaceWith = ReplaceWith("fromPropertiesFile(File(configFile), debug))", imports = arrayOf("java.io.File")))
        fun fromPropertiesFile(configFile: String, debug: Boolean = false): AppConfig = fromPropertiesFile(File(configFile), debug)

        fun fromPropertiesFileOrEnvironment(configFile: File?, debug: Boolean = false, overrides: Map<String, Any> = mapOf()): AppConfig =
                if (configFile != null) {
                    fromPropertiesFile(configFile, debug, overrides)
                } else {
                    fromEnvironment(debug, overrides)
                }

        fun fromPropertiesFile(configFile: File, debug: Boolean = false, overrides: Map<String, Any> = mapOf()): AppConfig {
            val params = Parameters().properties()
                    .setFile(configFile)
                    .setListDelimiterHandler(DefaultListDelimiterHandler(','))

            val configuration = FileBasedConfigurationBuilder(PropertiesConfiguration::class.java)
                    .configure(params)
                    .configuration
                    .apply {
                        overrides.forEach { (k, v) -> setProperty(k, v) }
                    }

            return AppConfig(configuration, debug)
        }

        fun fromEnvironment(debug: Boolean, overrides: Map<String, Any> = mapOf()): AppConfig = AppConfig(
                BaseConfiguration().apply { overrides.forEach { (k, v) -> setProperty(k, v) } },
                debug
        )
    }

    val exitOnFatalError: Boolean = getEnvOrBoolean("POSTCHAIN_EXIT_ON_FATAL_ERROR", "exit-on-fatal-error", false)

    /**
     * Configuration provider. Can only be used with manual mode
     */
    val nodeConfigProvider: String
        // properties | manual
        get() = config.getEnvOrStringProperty("POSTCHAIN_NODE_CONFIG_PROVIDER", "configuration.provider.node", "properties")

    /**
     * Database
     */
    val databaseDriverclass: String
        get() = config.getEnvOrStringProperty("POSTCHAIN_DB_DRIVER", "database.driverclass", "org.postgresql.Driver")

    val databaseUrl: String
        get() = config.getEnvOrStringProperty("POSTCHAIN_DB_URL", "database.url", "")

    val databaseSchema: String
        get() = config.getEnvOrStringProperty("POSTCHAIN_DB_SCHEMA", "database.schema", "public")

    val databaseUsername: String
        get() = config.getEnvOrStringProperty("POSTCHAIN_DB_USERNAME", "database.username", "")

    val databasePassword: String
        get() = config.getEnvOrStringProperty("POSTCHAIN_DB_PASSWORD", "database.password", "")

    val databaseReadConcurrency: Int
        get() = config.getEnvOrIntProperty("POSTCHAIN_DB_READ_CONCURRENCY", "database.readConcurrency", 10)

    val databaseBlockBuilderWriteConcurrency: Int
        get() = config.getEnvOrIntProperty("POSTCHAIN_DB_BLOCK_BUILDER_WRITE_CONCURRENCY", "database.blockBuilderWriteConcurrency", 8)

    val databaseSharedWriteConcurrency: Int
        get() = config.getEnvOrIntProperty("POSTCHAIN_DB_SHARED_WRITE_CONCURRENCY", "database.sharedWriteConcurrency", 2)

    val databaseBlockBuilderMaxWaitWrite: Duration
        get() = config.getEnvOrLongProperty("POSTCHAIN_DB_BLOCK_BUILDER_MAX_WAIT_WRITE", "database.blockBuilderMaxWaitWrite", 100).toDuration(DurationUnit.MILLISECONDS)

    val databaseSharedMaxWaitWrite: Duration
        get() = config.getEnvOrLongProperty("POSTCHAIN_DB_SHARED_MAX_WAIT_WRITE", "database.sharedMaxWaitWrite", 10_000).toDuration(DurationUnit.MILLISECONDS)

    val databaseSuppressCollationCheck: Boolean
        get() = config.getEnvOrBooleanProperty("POSTCHAIN_DB_SUPPRESS_COLLATION_CHECK", "database.suppressCollationCheck", false)

    val infrastructure: String
        // "base/ebft" is the default
        get() = config.getEnvOrStringProperty("POSTCHAIN_INFRASTRUCTURE", "infrastructure", Infrastructure.Ebft.get())

    val cryptoSystemClass: String = config.getEnvOrStringProperty("POSTCHAIN_CRYPTO_SYSTEM", "cryptosystem", Secp256K1CryptoSystem::class.qualifiedName!!)

    val cryptoSystem: CryptoSystem = newInstanceOf(cryptoSystemClass)

    /**
     * Pub/Priv keys
     */
    val privKey: String
        get() = config.getEnvOrStringProperty("POSTCHAIN_PRIVKEY", "messaging.privkey", "")

    val privKeyByteArray: ByteArray
        get() = privKey.hexStringToByteArray()

    val pubKey: String
        get() = config.getEnvOrStringProperty("POSTCHAIN_PUBKEY", "messaging.pubkey", "")

    val pubKeyByteArray: ByteArray
        get() = pubKey.hexStringToByteArray()

    val port: Int
        get() = config.getEnvOrIntProperty("POSTCHAIN_PORT", "messaging.port", DEFAULT_PORT)

    val genesisPeer: PeerInfo? get() {
        val genesisPubkey = getEnvOrString("POSTCHAIN_GENESIS_PUBKEY", "genesis.pubkey") ?: return null
        require(hasEnvOrKey("POSTCHAIN_GENESIS_HOST", "genesis.host")) { "Node configuration must contain genesis.host if genesis.pubkey is supplied" }
        require(hasEnvOrKey("POSTCHAIN_GENESIS_PORT", "genesis.port")) { "Node configuration must contain genesis.port if genesis.pubkey if supplied" }

        val genesisHost = getEnvOrString("POSTCHAIN_GENESIS_HOST", "genesis.host")!!
        val genesisPort = getEnvOrInt("POSTCHAIN_GENESIS_PORT", "genesis.port", 0)
        return PeerInfo(genesisHost, genesisPort, genesisPubkey.hexStringToByteArray())
    }

    // PCU feature toggle
    fun isPcuEnabled(): Boolean = getEnvOrBoolean("POSTCHAIN_PCU", "pcu", true)

    fun appliedConfigSendInterval(): Long = getEnvOrLong("POSTCHAIN_CONFIG_SEND_INTERVAL_MS", "applied-config-send-interval-ms", DEFAULT_APPLIED_CONFIG_SEND_INTERVAL_MS)

    /**
     * Wrappers for [Configuration] getters and other functionalities
     */
    fun getBoolean(key: String, defaultValue: Boolean = false) = config.getBoolean(key, defaultValue)
    fun getLong(key: String, defaultValue: Long = 0) = config.getLong(key, defaultValue)
    fun getInt(key: String, defaultValue: Int = 0) = config.getInt(key, defaultValue)
    fun getString(key: String, defaultValue: String = ""): String = config.getString(key, defaultValue)
    fun getStringArray(key: String): Array<String> = config.getStringArray(key)
    fun subset(prefix: String): Configuration = config.subset(prefix)
    fun getProperty(key: String): Any? = config.getProperty(key)
    fun getKeys(prefix: String): MutableIterator<String> = config.getKeys(prefix)
    fun containsKey(key: String) = config.containsKey(key)

    fun getEnvOrString(env: String, key: String, defaultValue: String) = config.getEnvOrStringProperty(env, key, defaultValue)
    fun getEnvOrString(env: String, key: String) = config.getEnvOrStringProperty(env, key)
    fun getEnvOrInt(env: String, key: String, defaultValue: Int) = config.getEnvOrIntProperty(env, key, defaultValue)
    fun getEnvOrLong(env: String, key: String, defaultValue: Long) = config.getEnvOrLongProperty(env, key, defaultValue)
    fun getEnvOrBoolean(env: String, key: String, defaultValue: Boolean) = config.getEnvOrBooleanProperty(env, key, defaultValue)
    fun hasEnvOrKey(env: String, key: String) = System.getenv(env) != null || config.containsKey(key)
}

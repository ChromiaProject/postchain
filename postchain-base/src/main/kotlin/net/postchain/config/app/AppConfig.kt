// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.app

import net.postchain.base.PeerInfo
import net.postchain.common.config.Config
import net.postchain.common.config.cryptoSystem
import net.postchain.common.config.cryptoSystemClass
import net.postchain.common.config.getEnvOrBooleanProperty
import net.postchain.common.config.getEnvOrIntProperty
import net.postchain.common.config.getEnvOrListProperty
import net.postchain.common.config.getEnvOrLongProperty
import net.postchain.common.config.getEnvOrStringProperty
import net.postchain.common.hexStringToByteArray
import net.postchain.core.Infrastructure
import net.postchain.crypto.CryptoSystem
import org.apache.commons.configuration2.BaseConfiguration
import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler
import java.io.File

/**
 * Wrapper to the generic [Configuration]
 * Adding some convenience fields, for example regarding database connection.
 */
class AppConfig(private val config: Configuration) : Config {

    @Suppress("UNUSED_PARAMETER")
    @Deprecated(message = "Use AppConfig(Configuration) instead",
            replaceWith = ReplaceWith("AppConfig(config)"))
    constructor(config: Configuration, debug: Boolean) : this(config)

    companion object {

        const val DEFAULT_PORT: Int = 9870
        const val DEFAULT_APPLIED_CONFIG_SEND_INTERVAL_MS: Long = 1_000

        @Suppress("UNUSED_PARAMETER")
        @Deprecated(message = "Use fromPropertiesFile(File) instead",
                replaceWith = ReplaceWith("fromPropertiesFile(File(configFile))", imports = arrayOf("java.io.File")))
        fun fromPropertiesFile(configFile: String, debug: Boolean = false): AppConfig = fromPropertiesFile(File(configFile))

        @Suppress("UNUSED_PARAMETER")
        @Deprecated(message = "Use fromPropertiesFileOrEnvironment(File, Map<String, Any>) instead",
                replaceWith = ReplaceWith("fromPropertiesFileOrEnvironment(configFile, overrides)"))
        fun fromPropertiesFileOrEnvironment(configFile: File?, debug: Boolean = false, overrides: Map<String, Any> = mapOf()): AppConfig =
                fromPropertiesFileOrEnvironment(configFile, overrides)

        fun fromPropertiesFileOrEnvironment(configFile: File?, overrides: Map<String, Any> = mapOf()): AppConfig =
                if (configFile != null) {
                    fromPropertiesFile(configFile, overrides)
                } else {
                    fromEnvironment(overrides)
                }

        @Suppress("UNUSED_PARAMETER")
        @Deprecated(message = "Use fromPropertiesFile(File, Map<String, Any>) instead",
                replaceWith = ReplaceWith("fromPropertiesFile(File(configFile), overrides)", imports = arrayOf("java.io.File")))
        fun fromPropertiesFile(configFile: File, debug: Boolean = false, overrides: Map<String, Any> = mapOf()): AppConfig = fromPropertiesFile(configFile, overrides)

        fun fromPropertiesFile(configFile: File, overrides: Map<String, Any> = mapOf()): AppConfig {
            val params = Parameters().properties()
                    .setFile(configFile)
                    .setListDelimiterHandler(DefaultListDelimiterHandler(','))

            val configuration = FileBasedConfigurationBuilder(PropertiesConfiguration::class.java)
                    .configure(params)
                    .configuration
                    .apply {
                        overrides.forEach { (k, v) -> setProperty(k, v) }
                    }

            return AppConfig(configuration)
        }

        @Suppress("UNUSED_PARAMETER")
        @Deprecated(message = "Use fromEnvironment(Map<String, Any>) instead",
                replaceWith = ReplaceWith("fromEnvironment(overrides)"))
        fun fromEnvironment(debug: Boolean, overrides: Map<String, Any> = mapOf()): AppConfig =
                fromEnvironment(overrides)

        fun fromEnvironment(overrides: Map<String, Any> = mapOf()): AppConfig = AppConfig(
                BaseConfiguration().apply { overrides.forEach { (k, v) -> setProperty(k, v) } }
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

    val databaseBlockBuilderMaxWaitWrite: Long
        get() = config.getEnvOrLongProperty("POSTCHAIN_DB_BLOCK_BUILDER_MAX_WAIT_WRITE", "database.blockBuilderMaxWaitWrite", 100)

    val databaseSharedMaxWaitWrite: Long
        get() = config.getEnvOrLongProperty("POSTCHAIN_DB_SHARED_MAX_WAIT_WRITE", "database.sharedMaxWaitWrite", 10_000)

    val databaseSuppressCollationCheck: Boolean
        get() = config.getEnvOrBooleanProperty("POSTCHAIN_DB_SUPPRESS_COLLATION_CHECK", "database.suppressCollationCheck", false)

    val infrastructure: String
        // "base/ebft" is the default
        get() = config.getEnvOrStringProperty("POSTCHAIN_INFRASTRUCTURE", "infrastructure", Infrastructure.Ebft.get())

    val cryptoSystemClass: String = config.cryptoSystemClass()

    val cryptoSystem: CryptoSystem = config.cryptoSystem()

    val readOnly: Boolean
        get() = config.getEnvOrBooleanProperty("POSTCHAIN_READ_ONLY", "readOnly", false)

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

    val hasPort: Boolean
        get() = hasEnvOrKey("POSTCHAIN_PORT", "messaging.port")

    val genesisPeer: PeerInfo?
        get() {
            val genesisPubkey = getEnvOrString("POSTCHAIN_GENESIS_PUBKEY", "genesis.pubkey") ?: return null
            require(hasEnvOrKey("POSTCHAIN_GENESIS_HOST", "genesis.host")) { "Node configuration must contain genesis.host if genesis.pubkey is supplied" }
            require(hasEnvOrKey("POSTCHAIN_GENESIS_PORT", "genesis.port")) { "Node configuration must contain genesis.port if genesis.pubkey if supplied" }

            val genesisHost = getEnvOrString("POSTCHAIN_GENESIS_HOST", "genesis.host")!!
            val genesisPort = getEnvOrInt("POSTCHAIN_GENESIS_PORT", "genesis.port", 0)
            return PeerInfo(genesisHost, genesisPort, genesisPubkey.hexStringToByteArray())
        }

    fun appliedConfigSendInterval(): Long = getEnvOrLong("POSTCHAIN_CONFIG_SEND_INTERVAL_MS", "applied-config-send-interval-ms", DEFAULT_APPLIED_CONFIG_SEND_INTERVAL_MS)

    val housekeepingIntervalMs
        get() = config.getEnvOrLongProperty("POSTCHAIN_HOUSEKEEPING_INTERVAL_MS", "housekeeping_interval_ms", 30_000)

    // -1: Disable tracking
    val trackedEbftMessageMaxKeepTimeMs
        get() = config.getEnvOrLongProperty("POSTCHAIN_TRACKED_EBFT_MESSAGE_MAX_KEEP_TIME_MS", "tracked_ebft_message_max_keep_time_ms", -1)


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
    fun getEnvOrListProperty(env: String, key: String, defaultValue: List<String>) = config.getEnvOrListProperty(env, key, defaultValue)
    fun hasEnvOrKey(env: String, key: String) = System.getenv(env) != null || config.containsKey(key)
}

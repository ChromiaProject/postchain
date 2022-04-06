// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.app

import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.ConfigurationUtils
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

/**
 * Wrapper to the generic [Configuration]
 * Adding some convenience fields, for example regarding database connection.
 */
class AppConfig(val config: Configuration) {

    companion object {

        fun fromPropertiesFile(configFile: String): AppConfig {
            val params = Parameters().properties()
                    .setFileName(configFile)
                    .setListDelimiterHandler(DefaultListDelimiterHandler(','))

            val configuration = FileBasedConfigurationBuilder<PropertiesConfiguration>(PropertiesConfiguration::class.java)
                    .configure(params)
                    .configuration

            configuration.setProperty("configDir", File(configFile).absoluteFile.parent)

            return AppConfig(configuration)
        }

        fun toPropertiesFile(config: Configuration, configFile: String) {
            ConfigurationUtils.dump(config, PrintWriter(FileWriter(configFile)))
        }
    }

    /**
     * This config dir
     */
    val configDir: String
        get() = config.getString("configDir")

    /**
     * Configuration provider
     */
    val nodeConfigProvider: String
        // legacy | manual | managed
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

}
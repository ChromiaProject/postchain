// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.app

import assertk.assert
import assertk.assertions.isEqualTo
import net.postchain.config.app.AssertsHelper.assertIsDefaultOrEqualsToEnvVar
import net.postchain.config.app.AssertsHelper.assertIsEmptyOrEqualsToEnvVar
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

class AppConfigTest {

    @Test
    fun testEmptyNodeConfig() {
        val appConfig = loadFromResource("empty-node-config.properties")

        assert(appConfig.nodeConfigProvider).isEqualTo("properties")
        assertEquals("org.postgresql.Driver", appConfig.databaseDriverclass)

        assertIsEmptyOrEqualsToEnvVar(appConfig.databaseUrl, "POSTCHAIN_DB_URL")
        assertIsDefaultOrEqualsToEnvVar(appConfig.databaseSchema, "public", "POSTCHAIN_DB_SCHEMA")
        assertIsEmptyOrEqualsToEnvVar(appConfig.databaseUsername, "POSTCHAIN_DB_USERNAME")
        assertIsEmptyOrEqualsToEnvVar(appConfig.databasePassword, "POSTCHAIN_DB_PASSWORD")
    }

    @Test
    fun testNoNodeConfig() {
        val appConfig = AppConfig.fromEnvironment(false)

        assert(appConfig.nodeConfigProvider).isEqualTo("properties")
        assertEquals("org.postgresql.Driver", appConfig.databaseDriverclass)

        assertIsEmptyOrEqualsToEnvVar(appConfig.databaseUrl, "POSTCHAIN_DB_URL")
        assertIsDefaultOrEqualsToEnvVar(appConfig.databaseSchema, "public", "POSTCHAIN_DB_SCHEMA")
        assertIsEmptyOrEqualsToEnvVar(appConfig.databaseUsername, "POSTCHAIN_DB_USERNAME")
        assertIsEmptyOrEqualsToEnvVar(appConfig.databasePassword, "POSTCHAIN_DB_PASSWORD")
    }

    private fun loadFromResource(filename: String): AppConfig {
        return AppConfig.fromPropertiesFile(
                File(javaClass.getResource("/net/postchain/config/$filename")!!.file))
    }
}
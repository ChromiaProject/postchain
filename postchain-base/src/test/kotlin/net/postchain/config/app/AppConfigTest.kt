// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.app

import assertk.assert
import assertk.assertions.isEqualTo
import net.postchain.config.app.AssertsHelper.assertIsDefaultOrEqualsToEnvVar
import net.postchain.config.app.AssertsHelper.assertIsEmptyOrEqualsToEnvVar
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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

    @Test
    fun overridesFileTest() {
        val appConfig = AppConfig.fromPropertiesFile(
                File(javaClass.getResource("/net/postchain/config/sub.properties")!!.file)
        )
        assertEquals("postchain", appConfig.databaseUsername)
        assertFalse(appConfig.containsKey("foo"))

        val appConfigOverridden = AppConfig.fromPropertiesFile(
                File(javaClass.getResource("/net/postchain/config/sub.properties")!!.file),
                overrides = mapOf("database.username" to "postchain2", "foo" to 123)
        )
        assertEquals("postchain2", appConfigOverridden.databaseUsername)
        assertEquals(123, appConfigOverridden.getInt("foo"))
    }

    @Test
    fun overridesEnvTest() {
        val appConfig = AppConfig.fromEnvironment(false)
        assertFalse(appConfig.containsKey("foo"))

        val appConfigOverridden = AppConfig.fromEnvironment(false, overrides = mapOf("foo" to 123))
        assertEquals(123, appConfigOverridden.getInt("foo"))
    }

    private fun loadFromResource(filename: String): AppConfig {
        return AppConfig.fromPropertiesFile(
                File(javaClass.getResource("/net/postchain/config/$filename")!!.file))
    }
}
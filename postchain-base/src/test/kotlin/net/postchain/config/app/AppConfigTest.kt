// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.app

import assertk.assert
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import net.postchain.config.app.AssertsHelper.assertIsDefaultOrEqualsToEnvVar
import net.postchain.config.app.AssertsHelper.assertIsEmptyOrEqualsToEnvVar
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AppConfigTest {

    @Test
    fun testEmptyNodeConfig() {
        val appConfig = loadFromResource("empty-node-config.properties")

        assert(appConfig.nodeConfigProvider).isEqualTo("properties")
        assert(appConfig.databaseDriverclass).isEmpty()

        assertIsEmptyOrEqualsToEnvVar(appConfig.databaseUrl, "POSTCHAIN_DB_URL")
        assertIsDefaultOrEqualsToEnvVar(appConfig.databaseSchema, "public", "POSTCHAIN_DB_SCHEMA")
        assertIsEmptyOrEqualsToEnvVar(appConfig.databaseUsername, "POSTCHAIN_DB_USERNAME")
        assertIsEmptyOrEqualsToEnvVar(appConfig.databasePassword, "POSTCHAIN_DB_PASSWORD")
    }

    @Test
    fun testClone() {
        val appConfig = loadFromResource("sub.properties")

        // full copy
        val clone = appConfig.cloneConfiguration()
        val originalSize = clone.size()
        assert(originalSize).isGreaterThan(0)

        // exclude unknown keys
        AppConfig.removeProperty(clone, "unknown_key")
        assert(clone.size()).isEqualTo(originalSize)

        // exclude 'container' properties
        // a) asserting that container properties exist
        clone.subset("container").also {
            assert(it.size()).isGreaterThan(0)
        }
        // b) removing container properties
        AppConfig.removeProperty(clone, "container")
        assert(clone.size()).isLessThan(originalSize)
        // c) asserting that container properties don't exist
        clone.subset("container").also {
            assertEquals(0, it.size())
        }
    }

    private fun loadFromResource(filename: String): AppConfig {
        return AppConfig.fromPropertiesFile(
                javaClass.getResource("/net/postchain/config/$filename")!!.file)
    }
}
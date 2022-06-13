// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import net.postchain.config.app.AppConfig
import net.postchain.config.app.AssertsHelper.assertIsDefaultOrEqualsToEnvVar
import net.postchain.config.app.AssertsHelper.assertIsEmptyOrEqualsToEnvVar
import org.junit.jupiter.api.Test

class NodeConfigTest {

    @Test
    fun ttt() {
        val m = mutableMapOf(1 to 10, 2 to 20, 3 to 30)
        val kk = m.keys.toSet()
        kk.forEach {
            println(m.remove(it))
        }
    }

    @Test
    fun testEmptyNodeConfig() {
        val appConfig = AppConfig.fromPropertiesFile(
                javaClass.getResource("/net/postchain/config/empty-node-config.properties").file)
        val nodeConfig = NodeConfig(appConfig)

        assertk.assert(appConfig.infrastructure).isEqualTo("ebft")

        assertk.assert(appConfig.databaseDriverclass).isEmpty()
        assertIsEmptyOrEqualsToEnvVar(appConfig.databaseUrl, "POSTCHAIN_DB_URL")
        assertIsDefaultOrEqualsToEnvVar(appConfig.databaseSchema, "public", "POSTCHAIN_DB_SCHEMA")
        assertIsEmptyOrEqualsToEnvVar(appConfig.databaseUsername, "POSTCHAIN_DB_USERNAME")
        assertIsEmptyOrEqualsToEnvVar(appConfig.databasePassword, "POSTCHAIN_DB_PASSWORD")

        assertIsEmptyOrEqualsToEnvVar(appConfig.privKey, "POSTCHAIN_PRIVKEY")
        assertIsEmptyOrEqualsToEnvVar(appConfig.pubKey, "POSTCHAIN_PUBKEY")

        assertk.assert(nodeConfig.peerInfoMap.entries).isEmpty()

    }
}
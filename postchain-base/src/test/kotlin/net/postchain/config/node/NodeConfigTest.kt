// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.config.app.AssertsHelper.assertIsDefaultOrEqualsToEnvVar
import net.postchain.config.app.AssertsHelper.assertIsEmptyOrEqualsToEnvVar
import net.postchain.core.NodeRid
import org.junit.jupiter.api.Test
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals

class NodeConfigTest {

    @Test
    fun testEmptyNodeConfig() {
        val appConfig = loadAppConfig("/net/postchain/config/empty-node-config.properties")
        val nodeConfig = NodeConfig(appConfig)

        assertThat(appConfig.infrastructure).isEqualTo("ebft")

        assertEquals("org.postgresql.Driver", appConfig.databaseDriverclass)
        assertIsEmptyOrEqualsToEnvVar(appConfig.databaseUrl, "POSTCHAIN_DB_URL")
        assertIsDefaultOrEqualsToEnvVar(appConfig.databaseSchema, "public", "POSTCHAIN_DB_SCHEMA")
        assertIsEmptyOrEqualsToEnvVar(appConfig.databaseUsername, "POSTCHAIN_DB_USERNAME")
        assertIsEmptyOrEqualsToEnvVar(appConfig.databasePassword, "POSTCHAIN_DB_PASSWORD")

        assertIsEmptyOrEqualsToEnvVar(appConfig.privKey, "POSTCHAIN_PRIVKEY")
        assertIsEmptyOrEqualsToEnvVar(appConfig.pubKey, "POSTCHAIN_PUBKEY")

        assertThat(nodeConfig.peerInfoMap.entries).isEmpty()
    }

    @Test
    fun testAncestors() {
        val nodeConfig = NodeConfig(loadAppConfig("/net/postchain/config/ancestors.properties"))
        // expected
        val n0 = NodeRid.fromHex("00")
        val n1 = NodeRid.fromHex("01")
        val n2 = NodeRid.fromHex("02")
        val n3 = NodeRid.fromHex("03")
        val brid0 = BlockchainRid.buildFromHex("90B136DFC51E08EE70ED929C620C0808D4230EC1015D46C92CCAA30772651DC0")
        val brid1 = BlockchainRid.buildFromHex("6CCD14B5A877874DDC7CA52BD3AEDED5543B73A354779224BBB86B0FD315B418")
        val brid2 = BlockchainRid.buildFromHex("4317338211726F61B281D62F0683FD55E355011B6E7495CF56F9E03059A3BC0A")
        val brid3 = BlockchainRid.buildFromHex("5CB8BBC2830DB208330BF409C53A6D15D8BCB3A6DA07C5327B1548F8538B12C1")
        val expected = mapOf(
                brid0 to mapOf(
                        brid1 to setOf(n0),
                        brid2 to setOf(n1)
                ),
                brid1 to mapOf(
                        brid3 to setOf(n2, n3)
                )
        )

        assertEquals(expected, nodeConfig.blockchainAncestors)
    }

    private fun loadAppConfig(path: String): AppConfig {
        return AppConfig.fromPropertiesFile(File(javaClass.getResource(path).file))
    }
}
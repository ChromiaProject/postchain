// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import net.postchain.config.app.AppConfig
import org.junit.Test

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

        // TODO: [POS-129]: Remove it
        assertk.assert(nodeConfig.blockchainConfigProvider).isEmpty()
        assertk.assert(nodeConfig.infrastructure).isEqualTo("base/ebft")

        assertk.assert(nodeConfig.databaseDriverclass).isEmpty()
        assertk.assert(nodeConfig.databaseUrl).isEmpty()
        assertk.assert(nodeConfig.databaseSchema).isEqualTo("public")
        assertk.assert(nodeConfig.databaseUsername).isEmpty()
        assertk.assert(nodeConfig.databasePassword).isEmpty()

        assertk.assert(nodeConfig.privKey).isEmpty()
        assertk.assert(nodeConfig.privKeyByteArray.isEmpty())
        assertk.assert(nodeConfig.pubKey).isEmpty()
        assertk.assert(nodeConfig.pubKeyByteArray.isEmpty())

        assertk.assert(nodeConfig.restApiBasePath).isEmpty()
        assertk.assert(nodeConfig.restApiPort).isEqualTo(7740)
        assertk.assert(nodeConfig.restApiSsl).isEqualTo(false)
        assertk.assert(nodeConfig.restApiSslCertificate).isEmpty()
        assertk.assert(nodeConfig.restApiSslCertificatePassword).isEmpty()

        assertk.assert(nodeConfig.peerInfoMap.entries).isEmpty()

        assertk.assert(nodeConfig.activeChainIds).isEmpty()
    }
}
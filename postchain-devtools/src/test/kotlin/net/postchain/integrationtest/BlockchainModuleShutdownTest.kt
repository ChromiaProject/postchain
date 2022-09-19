// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest

import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.getModules
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlockchainModuleShutdownTest : IntegrationTestSetup() {

    @Test
    fun testShutdown() {
        val nodes = createNodes(1, "/net/postchain/devtools/blockchain_module_shutdown.xml")
        val node = nodes[0]
        val module = node.getModules().find { it is ShutdownTestModule } as ShutdownTestModule
        assertFalse(module.hasShutdown)
        node.shutdown()
        assertTrue(module.hasShutdown)
    }
}

// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest

import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.getModules
import net.postchain.gtx.PatchOpsGTXModule
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GTXModuleTest : IntegrationTestSetup() {

    @Test
    fun testLifecycle() {
        val nodes = createNodes(1, "/net/postchain/devtools/blockchain_module_lifecycle.xml")
        val node = nodes[0]
        val module = node.getModules().find { it is LifecycleTestModule } as LifecycleTestModule
        assertTrue(module.hasDb)
        assertTrue(module.hasContext)
        assertTrue(module.hasSTX)
        assertTrue(module.hasExtInit)
        assertTrue(module.hasExtRelevantOps)
        assertTrue(module.hasExtConnect)
        assertFalse(module.hasExtDisconnect)
        assertFalse(module.hasShutdown)
        node.shutdown()
        assertTrue(module.hasExtDisconnect)
        assertTrue(module.hasShutdown)
    }

    @Test
    fun testLegacyPatchOpsModule() {
        val nodes = createNodes(1, "/net/postchain/devtools/blockchain_module_legacy.xml")
        val node = nodes[0]
        val legacyModule = node.getModules().find { it is PatchOpsGTXModule }
        assertNotNull(legacyModule)
    }
}

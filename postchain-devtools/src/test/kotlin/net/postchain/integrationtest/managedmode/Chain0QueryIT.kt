package net.postchain.integrationtest.managedmode

import net.postchain.devtools.ManagedModeTest
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class Chain0QueryIT : ManagedModeTest() {

    @Test
    fun checkChain0QueriesIsAvailable() {
        startManagedSystem(1, 0)

        assertNotNull(nodes[0].postchainContext.chain0QueryProvider())
    }
}

package net.postchain.integrationtest

import net.postchain.devtools.IntegrationTestSetup
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeout
import java.time.Duration

class FastRevoltSlowIntegrationTest : IntegrationTestSetup() {

    @Test
    fun `Verify that disconnected nodes are revolted against when status timeout has passed`() {
        val nodes = createNodes(4, "/net/postchain/devtools/fast_revolt/blockchain_config.xml")

        // Build a block so fast revolt mechanism can kick in
        buildBlock(1L, 0)

        // Shutdown next block builder
        nodes[1].shutdown()
        val liveNodes = nodes.sliceArray(listOf(0, 2, 3)).toList()

        // Revolt timeout = 35s, Fast revolt timeout = 2s
        // If block has not been built before 30s have passed we can safely fail the test before regular revolt is applied
        // Timeout is very generous to avoid flakiness on slow machines
        assertTimeout(Duration.ofSeconds(30)) { buildBlock(liveNodes, 1L, 1) }
    }
}

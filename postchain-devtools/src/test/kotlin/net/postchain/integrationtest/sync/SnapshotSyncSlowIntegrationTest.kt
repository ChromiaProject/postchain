package net.postchain.integrationtest.sync

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.concurrent.util.get
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.utils.configuration.NodeSetup
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.Test

class SnapshotSyncSlowIntegrationTest : ManagedModeTest() {

    override fun addNodeConfigurationOverrides(nodeSetup: NodeSetup) {
        super.addNodeConfigurationOverrides(nodeSetup)
        nodeSetup.nodeSpecificConfigs.setProperty("snapshotsync.threshold", 5)
    }

    @Test
    fun syncFromSnapshot() {
        startManagedSystem(4, 1)

        val initialConfig = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_4.xml")!!.readText())
        val c1 = startNewBlockchain(setOf(0, 1, 2, 3), setOf(4), null, rawBlockchainConfiguration = GtvEncoder.encodeGtv(initialConfig), blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())
        buildBlock(nodes.subList(0, 3),c1, 10)

        // Assert that we could snapshot sync the chain on the replica node
        restartNodeClean(4, c1, -1)
        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            assertThat(nodes[4].blockQueries().getLastBlockHeight().get()).isEqualTo(10)
        }
    }
}

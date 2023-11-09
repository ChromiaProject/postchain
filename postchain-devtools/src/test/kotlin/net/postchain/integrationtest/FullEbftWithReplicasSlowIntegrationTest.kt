// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class FullEbftWithReplicasSlowIntegrationTest : FullEbftSlowIntegrationTestCore() {

   @Disabled // TODO: [et]: Fix this tests
    @ParameterizedTest(name = "[{index}] nodesCount: {0}, blocksCount: {1}, txPerBlock: {2}, replicasCount: {3}")
    @CsvSource(
            "3, 1, 0, 1",
            "3, 10, 4, 3"
    )
    fun runXNodesWithYTxPerBlockAndReplica(nodesCount: Int, blocksCount: Int, txPerBlock: Int, replicasCount: Int) {
        logger.info {
            "runXNodesWithYTxPerBlockAndReplica(): " +
                    "nodesCount: $nodesCount, " +
                    "blocksCount: $blocksCount, " +
                    "txPerBlock: $txPerBlock, " +
                    "replicasCount: $replicasCount"
        }

        configOverrides.setProperty("testpeerinfos", createPeerInfosWithReplicas(nodesCount, replicasCount))
        createNodesWithReplicas(nodesCount, replicasCount, "/net/postchain/full_ebft/blockchain_config_$nodesCount.xml")

        runXNodesWithYTxPerBlockTest(blocksCount, txPerBlock)
    }
}

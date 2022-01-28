// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class FullEbftTestNightly : FullEbftTestNightlyCore() {

    @ParameterizedTest(name = "[{index}] nodesCount: {0}, blocksCount: {1}, txPerBlock: {2}")
    @CsvSource(
            "3, 1, 0", "3, 2, 0", "3, 10, 0", "3, 1, 10", "3, 2, 10", "3, 10, 10"
            , "4, 1, 0", "4, 2, 0", "4, 10, 0", "4, 1, 10", "4, 2, 10", "4, 10, 10"
            , "8, 1, 0", "8, 2, 0", "8, 10, 0", "8, 1, 10", "8, 2, 10", "8, 10, 10"
//            , "25, 100, 0"
    )
    fun runXNodesWithYTxPerBlock(nodesCount: Int, blocksCount: Int, txPerBlock: Int) {
        logger.info {
            "runXNodesWithYTxPerBlock(): " +
                    "nodesCount: $nodesCount, blocksCount: $blocksCount, txPerBlock: $txPerBlock"
        }

        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodesCount))
        createNodes(nodesCount, "/net/postchain/devtools/full_ebft/blockchain_config_$nodesCount.xml")

        runXNodesWithYTxPerBlockTest(blocksCount, txPerBlock)
    }
}

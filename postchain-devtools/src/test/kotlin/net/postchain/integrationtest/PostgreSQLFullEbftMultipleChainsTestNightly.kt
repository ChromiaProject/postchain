// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest

import net.postchain.integrationtest.multiple_chains.FullEbftMultipleChainsTestNightly
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PostgreSQLFullEbftMultipleChainsTestNightly : FullEbftMultipleChainsTestNightly() {

    @ParameterizedTest(name = "[{index}] nodesCount: 1, blocksCount: {0}, txPerBlock: {1}")
    @CsvSource(
            "1, 0", "2, 0", "10, 0"
            , "1, 1", "2, 1", "10, 1"
            , "1, 10", "2, 10", "10, 10"
    )
    fun runSingleNodeWithYTxPerBlock(blocksCount: Int, txPerBlock: Int) {
        runXNodesWithYTxPerBlock(
                1,
                blocksCount,
                txPerBlock,
                arrayOf(
                        "classpath:/net/postchain/multiple_chains/ebft_nightly/single_node/node0.properties"
                ),
                arrayOf(
                        "/net/postchain/devtools/multiple_chains/ebft_nightly/single_node/blockchain_config_1.xml",
                        "/net/postchain/devtools/multiple_chains/ebft_nightly/single_node/blockchain_config_2.xml"
                ))
    }

    @Disabled // TODO: Ignored due to the fact tests often fail
    @ParameterizedTest(name = "[{index}] nodesCount: 2, blocksCount: {0}, txPerBlock: {1}")
    @CsvSource(
            "1, 0", "2, 0", "10, 0"
            , "1, 1", "2, 1", "10, 1"
            , "1, 10", "2, 10", "10, 10"
    )
    fun runTwoNodesWithYTxPerBlock(blocksCount: Int, txPerBlock: Int) {
        runXNodesWithYTxPerBlock(
                2,
                blocksCount,
                txPerBlock,
                arrayOf(
                        "classpath:/net/postchain/multiple_chains/ebft_nightly/two_nodes/node0.properties",
                        "classpath:/net/postchain/multiple_chains/ebft_nightly/two_nodes/node1.properties"
                ),
                arrayOf(
                        "/net/postchain/devtools/multiple_chains/ebft_nightly/two_nodes/blockchain_config_1.xml",
                        "/net/postchain/devtools/multiple_chains/ebft_nightly/two_nodes/blockchain_config_2.xml"
                ))
    }

    @Disabled
    @ParameterizedTest(name ="[{index}] nodesCount: 5, blocksCount: {0}, txPerBlock: {1}")
    @CsvSource(
            "1, 0", "2, 0", "10, 0"
            , "1, 1", "2, 1", "10, 1"
            , "1, 10", "2, 10", "10, 10"
    )
    fun runFiveNodesWithYTxPerBlock(blocksCount: Int, txPerBlock: Int) {
        runXNodesWithYTxPerBlock(
                5,
                blocksCount,
                txPerBlock,
                arrayOf(
                        "classpath:/net/postchain/multiple_chains/ebft_nightly/five_nodes/node0.properties",
                        "classpath:/net/postchain/multiple_chains/ebft_nightly/five_nodes/node1.properties",
                        "classpath:/net/postchain/multiple_chains/ebft_nightly/five_nodes/node2.properties",
                        "classpath:/net/postchain/multiple_chains/ebft_nightly/five_nodes/node3.properties",
                        "classpath:/net/postchain/multiple_chains/ebft_nightly/five_nodes/node4.properties"
                ),
                arrayOf(
                        "/net/postchain/devtools/multiple_chains/ebft_nightly/five_nodes/blockchain_config_1.xml",
                        "/net/postchain/devtools/multiple_chains/ebft_nightly/five_nodes/blockchain_config_2.xml"
                ))
    }

}
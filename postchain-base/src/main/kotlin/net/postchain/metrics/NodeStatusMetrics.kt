package net.postchain.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import net.postchain.common.BlockchainRid
import net.postchain.ebft.NodeBlockState
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.logging.NODE_BLOCK_STATE_TAG
import net.postchain.logging.SOURCE_NODE_TAG
import net.postchain.logging.TARGET_NODE_TAG

internal const val REVOLTS_ON_NODE_METRIC_NAME = "RevoltsOnNode"
internal const val REVOLTS_ON_NODE_METRIC_DESCRIPTION = "Number of revolts towards node"
internal const val REVOLTS_BY_NODE_METRIC_NAME = "RevoltsByNode"
internal const val REVOLTS_BY_NODE_METRIC_DESCRIPTION = "Number of revolts node has done"
internal const val REVOLTS_BETWEEN_OTHER_NODES_METRIC_NAME = "RevoltsBetweenOtherNodes"
internal const val REVOLTS_BETWEEN_OTHER_NODES_METRIC_DESCRIPTION = "Number of revolts between other nodes"

class NodeStatusMetrics(chainIID: Long, blockchainRid: BlockchainRid) {

    val revoltsOnNode: Counter = getCounter(REVOLTS_ON_NODE_METRIC_NAME, REVOLTS_ON_NODE_METRIC_DESCRIPTION)

    val revoltsByNode: Counter = getCounter(REVOLTS_BY_NODE_METRIC_NAME, REVOLTS_BY_NODE_METRIC_DESCRIPTION)

    val revoltsBetweenOthers: Counter = getCounter(REVOLTS_BETWEEN_OTHER_NODES_METRIC_NAME, REVOLTS_BETWEEN_OTHER_NODES_METRIC_DESCRIPTION)

    private fun getCounter(name: String, description: String) = Counter.builder(name).description(description).register(Metrics.globalRegistry)

    private val statusChangeTimeBuilder: Timer.Builder = Timer.builder("statusChangeTime")
            .description("Time for status changes for different nodes")
            .tag(CHAIN_IID_TAG, chainIID.toString())
            .tag(BLOCKCHAIN_RID_TAG, blockchainRid.toHex())

    fun createStatusChangeTimeTimer(sourceNode: String, targetNode: String, state: NodeBlockState): Timer =
            statusChangeTimeBuilder
                    .tag(SOURCE_NODE_TAG, sourceNode)
                    .tag(TARGET_NODE_TAG, targetNode)
                    .tag(NODE_BLOCK_STATE_TAG, state.name)
                    .register(Metrics.globalRegistry)
}
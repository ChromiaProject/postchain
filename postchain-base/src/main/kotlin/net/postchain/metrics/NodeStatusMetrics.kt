package net.postchain.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics

internal const val REVOLTS_ON_NODE_METRIC_NAME = "RevoltsOnNode"
internal const val REVOLTS_ON_NODE_METRIC_DESCRIPTION = "Number of revolts towards node"
internal const val REVOLTS_BY_NODE_METRIC_NAME = "RevoltsByNode"
internal const val REVOLTS_BY_NODE_METRIC_DESCRIPTION = "Number of revolts node has done"
internal const val REVOLTS_BETWEEN_OTHER_NODES_METRIC_NAME = "RevoltsBetweenOtherNodes"
internal const val REVOLTS_BETWEEN_OTHER_NODES_METRIC_DESCRIPTION = "Number of revolts between other nodes"

class NodeStatusMetrics {

    val revoltsOnNode: Counter = getCounter(REVOLTS_ON_NODE_METRIC_NAME, REVOLTS_ON_NODE_METRIC_DESCRIPTION)

    val revoltsByNode: Counter = getCounter(REVOLTS_BY_NODE_METRIC_NAME, REVOLTS_BY_NODE_METRIC_DESCRIPTION)

    val revoltsBetweenOthers: Counter = getCounter(REVOLTS_BETWEEN_OTHER_NODES_METRIC_NAME, REVOLTS_BETWEEN_OTHER_NODES_METRIC_DESCRIPTION)

    private fun getCounter(name: String, description: String) = Counter.builder(name).description(description).register(Metrics.globalRegistry)
}
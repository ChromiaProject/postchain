// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

import net.postchain.core.BlockchainRid
import net.postchain.core.ByteArrayKey

sealed class RoutingRule

/**
 * Routing rule for cluster anchoring chain.
 * meaning: import headers from all chains running on a cluster.
 */
object ClusterAnchorRoutingRule: RoutingRule()

/**
 * Route messages from a specific topic of a specific chain.
 */
data class SpecificChainRoutingRule(
        val brid: BlockchainRid,
        val topics: List<String>
): RoutingRule()


/**
 * Route messages from entire network matching specific topic
 */
data class GlobalTopicRoutingRule(
        val topic: ByteArrayKey
): RoutingRule()






// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

import net.postchain.common.BlockchainRid

sealed class Route

/**
 * Route messages from a specific topic of a specific chain.
 */
data class SpecificChainRoute(
        val brid: BlockchainRid,
        val topics: List<String>
) : Route()

/**
 * Route messages from entire network matching specific topic
 */
data class GlobalTopicsRoute(
        val topics: List<String>
) : Route()

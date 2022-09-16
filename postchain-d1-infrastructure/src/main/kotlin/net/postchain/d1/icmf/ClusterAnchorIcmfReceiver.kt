// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

import net.postchain.common.BlockchainRid

class ClusterAnchorIcmfReceiver : IcmfReceiver<ClusterAnchorRoute, BlockchainRid, Long> {
    val localPipes = mutableMapOf<Long, ClusterAnchorIcmfPipe>()

    override fun getRelevantPipes(): List<IcmfPipe<ClusterAnchorRoute, BlockchainRid, Long>> {
        return localPipes.values.toList()
    }
}
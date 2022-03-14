// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

class ClusterAnchorIcmfReceiver: IcmfReceiver<ClusterAnchorRoute, Long> {
    val localPipes = mutableMapOf<Long, ClusterAnchorIcmfPipe>()

    override fun getRelevantPipes(): List<IcmfPipe<ClusterAnchorRoute, Long>> {
        return localPipes.values.toList()
    }
}
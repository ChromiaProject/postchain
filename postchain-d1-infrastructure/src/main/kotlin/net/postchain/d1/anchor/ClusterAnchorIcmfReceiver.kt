// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchor

class ClusterAnchorIcmfReceiver {
    val localPipes = mutableMapOf<Long, ClusterAnchorIcmfPipe>()

    fun getRelevantPipes(): List<ClusterAnchorIcmfPipe> {
        return localPipes.values.toList()
    }
}
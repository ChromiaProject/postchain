// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchor

class ClusterAnchorReceiver {
    val localPipes = mutableMapOf<Long, ClusterAnchorPipe>()

    fun getRelevantPipes(): List<ClusterAnchorPipe> {
        return localPipes.values.toList()
    }
}
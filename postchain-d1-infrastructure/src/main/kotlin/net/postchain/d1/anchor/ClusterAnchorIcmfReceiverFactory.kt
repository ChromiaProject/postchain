// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchor

import net.postchain.base.Storage

class ClusterAnchorIcmfReceiverFactory(storage: Storage) {
    val localDispatcher = ClusterAnchorDispatcher(storage)

    fun createReceiver(chainID: Long): ClusterAnchorIcmfReceiver {
        val recv = ClusterAnchorIcmfReceiver()
        localDispatcher.connectReceiver(chainID, recv)
        return recv
    }
}
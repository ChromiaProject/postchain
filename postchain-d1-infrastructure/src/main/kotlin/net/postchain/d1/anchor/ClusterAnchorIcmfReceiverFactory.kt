// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchor

import net.postchain.base.Storage

class ClusterAnchorIcmfReceiverFactory(storage: Storage) {
    val localDispatcher = LocalDispatcher(storage)

    fun createReceiver(chainID: Long): ClusterAnchorIcmfReceiver {
        val receiver = ClusterAnchorIcmfReceiver()
        localDispatcher.connectReceiver(chainID, receiver)
        return receiver
    }
}
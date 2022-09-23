// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchor

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.core.Storage

class ClusterAnchorDispatcher(val storage: Storage) {
    val receivers = mutableMapOf<Long, ClusterAnchorIcmfReceiver>()
    val chains = mutableMapOf<Long, BlockchainRid>()

    fun connectReceiver(chainID: Long, receiver: ClusterAnchorIcmfReceiver) {
        receivers[chainID] = receiver
        for ((c_chainID, brid) in chains) {
            if (c_chainID != chainID) {
                receiver.localPipes[c_chainID] = ClusterAnchorIcmfPipe(
                        brid,
                        storage,
                        c_chainID
                )
            }
        }
    }

    fun connectChain(chainID: Long) {
        val brid = withReadConnection(storage, chainID) {
            DatabaseAccess.of(it).getBlockchainRid(it)!!
        }

        for ((recID, rec) in receivers) {
            if ((recID != chainID) && (chainID !in rec.localPipes)) {
                rec.localPipes[chainID] = ClusterAnchorIcmfPipe(
                        brid,
                        storage,
                        chainID
                )
            }
        }

        chains[chainID] = brid
    }

    fun disconnectChain(chainID: Long) {
        receivers.remove(chainID)
        for (r in receivers.values) {
            if (chainID in r.localPipes)
                r.localPipes.remove(chainID)
        }
        chains.remove(chainID)
    }

    fun afterCommit(chainID: Long, height: Long) {
        // TODO: prefetch packet
        for (r in receivers.values) {
            r.localPipes[chainID]?.setHighestSeenHeight(height)
        }
    }

}
// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

import net.postchain.base.Storage
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.data.EMPTY_HASH
import net.postchain.core.BlockchainRid
import net.postchain.gtv.GtvByteArray

class IcmfLocalDispatcher(val storage: Storage) {
    val receivers = mutableMapOf<Long, ClusterAnchorIcmfReceiver>()
    val chains = mutableMapOf<Long, BlockchainRid>()

    fun connectReceiver(chainID: Long, receiver: ClusterAnchorIcmfReceiver) {
        receivers[chainID] = receiver
        for ((c_chainID, brid) in chains) {
            if (c_chainID != chainID) {
                val pipeID = PipeID(ClusterAnchorRoute, GtvByteArray(brid.data))
                receiver.localPipes[c_chainID] = ClusterAnchorIcmfPipe(
                        pipeID,
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

        val pipeID = PipeID(ClusterAnchorRoute, GtvByteArray(brid.data))
        for ((recID, rec) in receivers) {
            if ((recID != chainID) && (chainID !in rec.localPipes)) {
                rec.localPipes[chainID] = ClusterAnchorIcmfPipe(
                        pipeID,
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
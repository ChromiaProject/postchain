// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

import net.postchain.base.Storage
import net.postchain.common.data.EMPTY_HASH
import net.postchain.core.BlockchainRid
import net.postchain.gtv.GtvByteArray

class IcmfLocalDispatcher(val storage: Storage) {
    val receivers = mutableMapOf<Long, ClusterAnchorIcmfReceiver>()

    fun connectReceiver(chainID: Long, receiver: ClusterAnchorIcmfReceiver) {
        receivers[chainID] = receiver
    }

    fun connectChain(chainID: Long) {
        // TODO: get BRID from chainID
        val brid = BlockchainRid(EMPTY_HASH)
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
    }

    fun disconnectChain(chainID: Long) {
        receivers.remove(chainID)
        for (r in receivers.values) {
            if (chainID in r.localPipes)
                r.localPipes.remove(chainID)
        }
    }

    fun afterCommit(chainID: Long, height: Long) {
        // TODO: prefetch packet
        for (r in receivers.values) {
            r.localPipes[chainID]?.setHighestSeenHeight(height)
        }
    }

}
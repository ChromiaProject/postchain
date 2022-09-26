// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchor

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.core.Storage

class ClusterAnchorDispatcher(private val storage: Storage) {
    private val receivers = mutableMapOf<Long, ClusterAnchorReceiver>()
    private val chains = mutableMapOf<Long, BlockchainRid>()

    fun connectReceiver(chainID: Long, receiver: ClusterAnchorReceiver) {
        receivers[chainID] = receiver
        chains.filterKeys { it != chainID }.forEach { (currentChainID, brid) ->
            receiver.localPipes[currentChainID] = ClusterAnchorPipe(
                    brid,
                    storage,
                    currentChainID
            )
        }
    }

    fun connectChain(chainID: Long) {
        val brid = withReadConnection(storage, chainID) {
            DatabaseAccess.of(it).getBlockchainRid(it)!!
        }

        receivers.filter { it.key != chainID && (chainID !in it.value.localPipes) }.values.forEach {
            it.localPipes[chainID] = ClusterAnchorPipe(
                    brid,
                    storage,
                    chainID
            )
        }

        chains[chainID] = brid
    }

    fun disconnectChain(chainID: Long) {
        receivers.remove(chainID)
        receivers.values.forEach {
            it.localPipes.remove(chainID)
        }
        chains.remove(chainID)
    }

    fun afterCommit(chainID: Long, height: Long) {
        // TODO: prefetch packet
        receivers.values.forEach {
            it.localPipes[chainID]?.setHighestSeenHeight(height)
        }
    }
}

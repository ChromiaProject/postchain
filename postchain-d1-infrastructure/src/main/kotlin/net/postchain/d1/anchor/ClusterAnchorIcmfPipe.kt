// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.anchor

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockEContext
import net.postchain.core.Storage
import java.lang.Long.max
import java.util.concurrent.atomic.AtomicLong

class ClusterAnchorIcmfPipe(
    val id: BlockchainRid,
    protected val storage: Storage,
    protected val chainID: Long
) {
    private val highestSeen = AtomicLong(-1L)
    private val lastCommitted = AtomicLong(-1L)

    // TODO: prefetch packet in dispatcher instead of just setting height
    fun setHighestSeenHeight(height: Long) = highestSeen.set(height)

    fun mightHaveNewPackets() = highestSeen.get() > lastCommitted.get()

    fun fetchNext(currentPointer: Long): ClusterAnchorPacket? {
        return withReadConnection(storage, chainID) { eContext ->
            val dba = DatabaseAccess.of(eContext)

            val blockRID = dba.getBlockRID(eContext, currentPointer + 1)
            if (blockRID != null) {
                highestSeen.getAndUpdate { max(it, currentPointer + 1) }
                // Get raw data
                val rawHeader = dba.getBlockHeader(eContext, blockRID)  // Note: expensive
                val rawWitness = dba.getWitnessData(eContext, blockRID)

                ClusterAnchorPacket(currentPointer, blockRID, rawHeader, rawWitness)
            } else {
                null
            }
        }
    }

    fun markTaken(currentPointer: Long, bctx: BlockEContext) {
        bctx.addAfterCommitHook {
            lastCommitted.getAndUpdate { max(it, currentPointer) }
        }
    }

}
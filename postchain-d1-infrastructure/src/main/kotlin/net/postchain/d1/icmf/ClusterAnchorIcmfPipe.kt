// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

import net.postchain.base.Storage
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.core.BlockEContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvFactory
import java.lang.Long.max

class ClusterAnchorIcmfPipe(
        override val id: PipeID<ClusterAnchorRoute>,
        protected val storage: Storage,
        protected val chainID: Long
): IcmfPipe<ClusterAnchorRoute, Long> {
    private var highestSeen = -1L
    private var lastCommitted = -1L
    private val lock = Any()

    // TODO: prefetch packet in dispatcher instead of just setting height
    fun setHighestSeenHeight(height: Long) {
        synchronized(lock) {
            highestSeen = height
        }
    }

    override fun mightHaveNewPackets(): Boolean {
        synchronized(lock) {
            return (highestSeen > lastCommitted)
        }
    }

    override fun fetchNext(currentPointer: Long): IcmfPacket? {
        return withReadConnection(storage, chainID) { eContext ->
            val dba = DatabaseAccess.of(eContext)

            val blockRID = dba.getBlockRID(eContext, currentPointer + 1)
            if (blockRID != null) {
                synchronized(lock) {
                    highestSeen = max(highestSeen, currentPointer + 1)
                }
                val gtvBlockRid: Gtv = GtvByteArray(blockRID)

                // Get raw data
                val rawHeader = dba.getBlockHeader(eContext, blockRID)  // Note: expensive
                val rawWitness = dba.getWitnessData(eContext, blockRID)

                // Transform raw bytes to GTV
                val gtvHeader: Gtv = GtvDecoder.decodeGtv(rawHeader)
                val gtvWitness: Gtv = GtvFactory.gtv(rawWitness)  // This is a primitive GTV encoding, but all we have

                IcmfPacket.build(currentPointer, gtvBlockRid, gtvHeader, gtvWitness)
            } else {
                null
            }
        }
    }

    override fun markTaken(currentPointer: Long, bctx: BlockEContext) {
        bctx.addAfterCommitHook {
            synchronized(lock) {
                lastCommitted = max(currentPointer, lastCommitted)
            }
        }
    }

}
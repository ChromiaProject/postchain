// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

import net.postchain.base.Storage
import net.postchain.core.BlockEContext
import net.postchain.gtv.Gtv

class RemoteChainIcmfPipe(
        override val id: PipeID<SpecificChainRoute>,
        val fetcher: RemoteChainFetcher
): IcmfPipe<SpecificChainRoute, Long> {
    val brid = (id.route as SpecificChainRoute).brid
    val topics = (id.route as SpecificChainRoute).topics

    var packets = listOf<IcmfPacket>()

    override fun mightHaveNewPackets(): Boolean {
        return packets.size > 0
    }

    override fun markTaken(currentPointer: Long, bctx: BlockEContext) {
        TODO("Not yet implemented")
    }

    override fun fetchNext(currentPointer: Long): IcmfPacket? {
        return null
    }

}
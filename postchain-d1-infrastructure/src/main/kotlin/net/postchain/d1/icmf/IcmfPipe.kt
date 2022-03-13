// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

import net.postchain.core.BlockEContext
import net.postchain.gtv.Gtv

data class PipeID (
        val routingRule: RoutingRule,
        val key: Gtv
) {
    override fun toString(): String {
        // TODO
        return "PipeID(TODO)"
    }
}

interface IcmfPipe {
    val id: PipeID
    fun mightHaveNewPackets(): Boolean
    fun fetchNext(currentPointer: Gtv): IcmfPacket?
    fun markTaken(currentPointer: Gtv, bctx: BlockEContext)
}
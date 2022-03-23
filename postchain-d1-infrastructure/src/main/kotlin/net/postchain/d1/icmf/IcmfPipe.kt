// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

import net.postchain.core.BlockEContext
import net.postchain.core.BlockchainRid

data class PipeID<RT: Route> (
        val route: RT,
        val brid: BlockchainRid
)

interface IcmfPipe<RT: Route, PtrT> {
    val id: PipeID<RT>
    fun mightHaveNewPackets(): Boolean
    fun fetchNext(currentPointer: PtrT): IcmfPacket?
    fun markTaken(currentPointer: PtrT, bctx: BlockEContext)
}
// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

import net.postchain.core.BlockEContext

interface IcmfPipe<RT : Route, IdT, PtrT> {
    val route: RT
    val id: IdT
    fun mightHaveNewPackets(): Boolean

    /**
     * Fetches next packets with pointer greater than currentPointer
     */
    fun fetchNext(currentPointer: PtrT): IcmfPackets<PtrT>?
    fun markTaken(currentPointer: PtrT, bctx: BlockEContext)
}

package net.postchain.d1.icmf

import net.postchain.core.BlockEContext

class GlobalTopicPipe(override val route: GlobalTopicRoute, override val id: String) :  IcmfPipe<GlobalTopicRoute, String, Long> {
    override fun mightHaveNewPackets(): Boolean {
        TODO("Not yet implemented")
    }

    override fun fetchNext(currentPointer: Long): IcmfPacket? {
        TODO("Not yet implemented")
    }

    override fun markTaken(currentPointer: Long, bctx: BlockEContext) {
        TODO("Not yet implemented")
    }
}

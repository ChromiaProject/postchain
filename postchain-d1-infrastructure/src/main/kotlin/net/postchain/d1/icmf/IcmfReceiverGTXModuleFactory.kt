package net.postchain.d1.icmf

import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.gtv.Gtv
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXModuleFactory

class IcmfReceiverGTXModuleFactory : GTXModuleFactory {
    override fun makeModule(config: Gtv, blockchainRID: BlockchainRid): GTXModule {
        val topics = config["icmf"]!!["receiver"]!!["topics"]!!.asArray().map { it.asString() }
        if (topics.isEmpty()) {
            throw UserMistake("No topics configured for icmf")
        }

        return IcmfReceiverGTXModule(topics)
    }
}
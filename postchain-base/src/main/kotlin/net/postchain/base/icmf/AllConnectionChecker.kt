package net.postchain.base.icmf

import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainRid

/**
* The simplest possible ICMF pipe connection strategy, just say yes to everything
 */
class AllConnectionChecker : ConnectionChecker {

    /**
     * Say yes to everything
     */
    override fun shouldConnect(listeningChainRid: BlockchainRid, bcProcess: BlockchainProcess, controller: IcmfController): Boolean {
        val sourceChainIid: Long = bcProcess.getEngine().getConfiguration().chainID
        if (controller.icmfReceiver.isSourceAndTargetConnected(sourceChainIid, listeningChainRid)) {
            controller.logger.warn("shouldConnect() -- source chain id: $sourceChainIid and listening chain id: ${listeningChainRid.toShortHex()} already connected")
            return false
        }
        return true
    }
}
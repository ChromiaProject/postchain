package net.postchain.base.icmf

import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainRid

/**
 * Stupid interface, could have been Lambda but I like documentation so here it is.
 */
interface ConnectionChecker {

    /**
     * @param listeningChainRid is the potential listener chain of the connection
     * @param bcProcess represents the potential source process
     * @param controller is the [IcmfController] (could be needed for something)
     * @return true if a we should connect target with source
     */
    fun shouldConnect(listeningChainRid: BlockchainRid, bcProcess: BlockchainProcess, controller: IcmfController): Boolean
}
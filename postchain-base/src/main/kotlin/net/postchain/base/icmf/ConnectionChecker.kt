package net.postchain.base.icmf

import net.postchain.base.BlockchainRelatedInfo
import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainRid

/**
 * Stupid interface, could have been Lambda but I like documentation so here it is.
 */
interface ConnectionChecker {

    /**
     * @param sourceChain represents the potential source process
     * @param listeningChain is the potential listener chain of the connection
     * @param controller is the [IcmfController] (could be needed for something)
     * @return true if a we should connect source and listening chain
     */
    fun shouldConnect(sourceChain: Long,
                      listeningChain: Long,
                      controller: IcmfController
    ): Boolean


    /**
     * @param otherSourceConnChecker is a conn checker for another (potential) source chain
     * @return true if we want to use the other chain as a source chain (only based on the given conn checker)
     */
    fun shouldConnect(otherSourceConnChecker: ConnectionChecker): Boolean
}
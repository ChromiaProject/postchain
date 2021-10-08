package net.postchain.config.blockchain

import mu.KLogging
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.icmf.IcmfController
import net.postchain.core.EContext
import net.postchain.core.ProgrammerMistake

/**
 * Just a place to put some common things to make the code more DRY.
 */
abstract class AbstractBlockchainConfigurationProvider: BlockchainConfigurationProvider {

    private val icmfController = IcmfController()

    companion object : KLogging()

    override fun getIcmfController(): IcmfController {
        return this.icmfController
    }

    fun check(eContext: EContext, chainId: Long) {
        if (eContext.chainID != chainId) {
            throw ProgrammerMistake("Mismatch between eCtx: ${eContext.chainID} and given chainIid: $chainId. Probably a bug")
        }
    }

    /**
     * @return the active height of the given chain, where "active height" is defined as the future height of block we
     * are currently building.
     */
    fun getActiveBlocksHeight(eContext: EContext, dba: DatabaseAccess): Long {
        val lastSavedBlockHeight = dba.getLastBlockHeight(eContext)
        return lastSavedBlockHeight + 1 // active block height is NOT the last saved block height
    }
}
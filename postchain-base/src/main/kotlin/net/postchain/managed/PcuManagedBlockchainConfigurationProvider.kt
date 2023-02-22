// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import mu.KLogging
import net.postchain.base.data.DatabaseAccess
import net.postchain.config.blockchain.AbstractBlockchainConfigurationProvider
import net.postchain.config.blockchain.ManualBlockchainConfigurationProvider
import net.postchain.core.EContext

class PcuManagedBlockchainConfigurationProvider : AbstractBlockchainConfigurationProvider() {

    private lateinit var dataSource: ManagedNodeDataSource
    private val systemProvider = ManualBlockchainConfigurationProvider() // Used mainly to access Chain0 (we don't want to use Chain0 to check it's own config changes, too strange)

    companion object : KLogging()

    fun setDataSource(dataSource: ManagedNodeDataSource) {
        this.dataSource = dataSource
    }

    override fun getActiveBlocksConfiguration(eContext: EContext, chainId: Long): ByteArray? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        return if (chainId == 0L) {
            systemProvider.getActiveBlocksConfiguration(eContext, chainId)
        } else {
            if (::dataSource.isInitialized) {
                getActiveBlockPendingConfiguration(eContext)
            } else {
                throw IllegalStateException("Using managed blockchain configuration provider before it's properly initialized")
            }
        }
    }

    override fun activeBlockNeedsConfigurationChange(eContext: EContext, chainId: Long): Boolean {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        return if (chainId == 0L) {
            systemProvider.activeBlockNeedsConfigurationChange(eContext, chainId)
        } else {
            if (::dataSource.isInitialized) {
                getActiveBlockPendingConfiguration(eContext) != null
            } else {
                throw IllegalStateException("Using managed blockchain configuration provider before it's properly initialized")
            }
        }
    }

    override fun getHistoricConfigurationHeight(eContext: EContext, chainId: Long, historicBlockHeight: Long): Long? {
        requireChainIdToBeSameAsInContext(eContext, chainId)
        return systemProvider.getHistoricConfigurationHeight(eContext, chainId, historicBlockHeight)
    }

    override fun getHistoricConfiguration(eContext: EContext, chainId: Long, historicBlockHeight: Long): ByteArray? {
        requireChainIdToBeSameAsInContext(eContext, chainId)
        return systemProvider.getHistoricConfiguration(eContext, chainId, historicBlockHeight)
    }

    // --------- Private --------

    private fun getActiveBlockPendingConfiguration(eContext: EContext): ByteArray? {
        val dba = DatabaseAccess.of(eContext)
        val blockchainRid = dba.getBlockchainRid(eContext)
        val activeHeight = getActiveBlocksHeight(eContext, dba)
        return dataSource.getPendingBlockchainConfiguration(blockchainRid!!, activeHeight)
    }

    // TODO: [POS-620]: Review it
    override fun findNextConfigurationHeight(eContext: EContext, height: Long): Long? {
        val db = DatabaseAccess.of(eContext)
        val brid = db.getBlockchainRid(eContext)
        return dataSource.findNextConfigurationHeight(brid!!.data, height)
    }

}
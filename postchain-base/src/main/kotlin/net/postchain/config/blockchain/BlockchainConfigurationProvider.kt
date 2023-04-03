// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.blockchain

import net.postchain.base.data.DatabaseAccess
import net.postchain.core.EContext

/**
 * Provides configuration of specific blockchain like block-strategy, configuration-factory,
 * signers and gtx specific args
 *
 * Note: we are sending the [EContext] as a parameter, and it holds the chain Iid, but we are also providing
 *       the chain Iid as a separate parameter, to make it clear that we can get ANY chain from the config provider.
 */
interface BlockchainConfigurationProvider {

    /**
     * @return the configuration we must use for the "active" block (=the block we are currently building)
     */
    fun getActiveBlocksConfiguration(eContext: EContext, chainId: Long): ByteArray?

    /**
     * @return true if the given chain will need a new configuration for the "active" block
     * (=the block we are currently building)
     */
    fun activeBlockNeedsConfigurationChange(eContext: EContext, chainId: Long): Boolean

    /**
     * Use this when you want to know what configuration height was used for a "historic block"
     *
     * @param eContext
     * @param chainId is the chain we are interested in
     * @param historicBlockHeight is the block height we want to see the configuration for
     * @return the height of the configuration for the given historicBlockHeight.
     */
    fun getHistoricConfigurationHeight(eContext: EContext, chainId: Long, historicBlockHeight: Long): Long?

    /**
     * Use this when you want to fetch the configuration that was used for a "historic block"
     *
     * @param eContext
     * @param chainId is the chain we are interested in
     * @param historicBlockHeight is the block height we want to see the configuration for
     * @return the height of the configuration for the given historicBlockHeight.
     */
    fun getHistoricConfiguration(eContext: EContext, chainId: Long, historicBlockHeight: Long): ByteArray?

    fun findNextConfigurationHeight(eContext: EContext, height: Long): Long?

    /**
     * @return the active height of the given chain, where "active height" is defined as the future height of block we
     * are currently building.
     */
    fun getActiveBlocksHeight(eContext: EContext, dba: DatabaseAccess): Long
}

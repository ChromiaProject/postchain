// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.blockchain

import net.postchain.base.icmf.IcmfController
import net.postchain.core.EContext

/**
 * Provides configuration of specific blockchain like block-strategy, configuration-factory,
 * signers and gtx specific args
 */
interface BlockchainConfigurationProvider {
    fun getConfiguration(eContext: EContext, chainId: Long): ByteArray?
    fun needsConfigurationChange(eContext: EContext, chainId: Long): Boolean
    fun getIcmfController(): IcmfController // We need to use the same [IcmfController] for all BC configuration
                                            // or else they won't "see" each other (and be unable to connect).

}
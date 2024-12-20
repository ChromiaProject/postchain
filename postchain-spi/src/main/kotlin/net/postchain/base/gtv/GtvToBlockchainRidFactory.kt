// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.gtv

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.common.BlockchainRid
import net.postchain.gtv.Gtv

object GtvToBlockchainRidFactory {
    /**
     * Calculates blockchain RID by the given blockchain configuration.
     *
     * @param data is the [Gtv] data of the configuration
     * @return the blockchain RID
     */
    fun calculateBlockchainRid(data: BlockchainConfigurationData): BlockchainRid {
        // Need to calculate it the RID, and we do it the usual way (same as merkle root of block)
        return BlockchainRid(data.configHash)
    }
}

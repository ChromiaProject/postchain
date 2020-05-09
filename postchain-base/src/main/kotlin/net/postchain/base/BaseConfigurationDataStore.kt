// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import mu.KLogging
import net.postchain.base.data.DatabaseAccess
import net.postchain.core.ConfigurationDataStore
import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory

object BaseConfigurationDataStore : KLogging(), ConfigurationDataStore {


    override fun findConfigurationHeightForBlock(context: EContext, height: Long): Long? {
        return DatabaseAccess.of(context).findConfigurationHeightForBlock(context, height)
    }

    override fun getConfigurationData(context: EContext, height: Long): ByteArray? {
        return DatabaseAccess.of(context).getConfigurationData(context, height)
    }

    override fun addConfigurationData(context: EContext, height: Long, binData: ByteArray): BlockchainRid {
        return addConfigurationDataInternal(context, height, binData, GtvFactory.decodeGtv(binData))
    }

    override fun addConfigurationData(context: EContext, height: Long, gtvData: Gtv): BlockchainRid {
        return addConfigurationDataInternal(context, height, GtvEncoder.encodeGtv(gtvData), gtvData)
    }

    /**
     * We must figure out the Blockchain RID from the [Gtv] configuration if it's not in the DB.
     * The [BlockchainRidFactory] will create it and add it to DB if it does not exist.
     * (So, end result is that BC RID will always be in the "blockchains" table after this function has been run)
     *
     * @param context needed for the DB.
     * @param height is the lowest block height from which the configuration should be used.
     * @param binData is the configuration as binary.
     * @param gtvData is the configuration in GTV format.
     * @return the Blockchain RID of the chain.
     */
    private fun addConfigurationDataInternal(
            context: EContext,
            height: Long,
            binData: ByteArray,
            gtvData: Gtv
    ): BlockchainRid {
        val bcRidInDB = DatabaseAccess.of(context).getBlockchainRID(context)
        val bcRid = if (height > 0L) {
            bcRidInDB ?: throw IllegalStateException("Chain ${context.chainID} doesn't have a BC RID, but must have since we are creating a configuration for height $height")
        } else {
            // This is the first conf for the chain.
            if (bcRidInDB == null) {
                val newBcRid = BlockchainRidFactory.resolveBlockchainRID(gtvData, context)
                logger.info("Creating initial configuration for chain ${context.chainID} with BC RID: $newBcRid")
                newBcRid
            } else {
                logger.warn { "How did the BC RID for chain ${context.chainID} get in here before chain configuration were added? Investigate. " }
                bcRidInDB
            }
        }

        DatabaseAccess.of(context).addConfigurationData(context, height, binData)
        return bcRid
    }
}
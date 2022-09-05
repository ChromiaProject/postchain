package net.postchain.config.blockchain

import net.postchain.common.BlockchainRid

/**
 * A simple BC configuration reader that remember what height you looked for last time and updates the config when
 * there is a new config at the current block height.
 *
 * Note:
 * You still have to be careful with this tool since it cannot accept you going back in time and look for "old"
 * configs, it is only fast (a generally works as intended) when you move forward in block heights.
 * Configurations for lower heights will be discarded, only the most recent height's configuration will be kept
 */
interface HeightAwareConfigReader {

    /**
     * @param chainIid is the BC we are interested in.
     * @param blockHeight is the block height we are at right now (to find the correct version of the config)
     * @param confKey is the configuration we are looking for
     * @return the string representation of the configuration value, if found.
     */
    fun getSetting(chainIid: Long, blockHeight: Long, confKey: String): String?

    /**
     * @param chainIid is the BC we are interested in.
     * @param blockHeight is the block height we are at right now (to find the correct version of the config)
     * @param confKey is the configuration we are looking for
     * @return a list of [BlockchainRid] from the configuration values, or empty list
     */
    fun getBcRidArray(chainIid: Long, blockHeight: Long, confKey: String): List<BlockchainRid>
}
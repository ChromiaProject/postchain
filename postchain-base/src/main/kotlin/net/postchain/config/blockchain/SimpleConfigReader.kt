package net.postchain.config.blockchain

import net.postchain.common.BlockchainRid

/**
 * A very simple BC configuration reader.
 */
interface SimpleConfigReader {

    /**
     * @param chainIid is the BC we are interested in.
     * @param confKey is the configuration we are looking for
     * @return the string representation of the configuration value, if found.
     */
    fun getSetting(chainIid: Long, confKey: String): String?

    /**
     * @param chainIid is the BC we are interested in.
     * @param confKey is the configuration we are looking for
     * @return a list of [BlockchainRid] from the configuration values, or empty list
     */
    fun getBcRidArray(chainIid: Long, confKey: String): List<BlockchainRid>
}
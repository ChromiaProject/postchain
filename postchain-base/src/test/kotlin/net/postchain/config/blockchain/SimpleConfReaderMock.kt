package net.postchain.config.blockchain

import net.postchain.common.BlockchainRid

/**
 * This will mock only one configuration, so create a different mock for every BC.
 *
 * (Please modify this class so it can be used for anything where you want to mock a [SimpleConfigReader])
 */
class SimpleConfReaderMock(
    private val returnVal: String?, // The value we return on questions
    private val bcRids: List<BlockchainRid> = mutableListOf() // What the getBcRidArray() always returns
    ) : SimpleConfigReader {

    override fun getSetting(chainIid: Long, confKey: String): String? {
        return returnVal
    }

    override fun getBcRidArray(chainIid: Long, confKey: String): List<BlockchainRid>  {
        return bcRids
    }
}
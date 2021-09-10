package net.postchain.config.blockchain

import net.postchain.core.BlockchainRid

class SimpleConfReaderMock(
    private val returnVal: String // The value we always should return
    ) : SimpleConfigReader {

    override fun getSetting(chainIid: Long, confKey: String): String? {
        return returnVal
    }

    override fun getBcRidArray(chainIid: Long, confKey: String): List<BlockchainRid>  {
        return listOf()
    }
}
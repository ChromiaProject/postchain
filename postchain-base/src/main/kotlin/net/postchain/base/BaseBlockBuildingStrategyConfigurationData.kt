package net.postchain.base

import net.postchain.base.configuration.KEY_BLOCKSTRATEGY_MAXBACKOFFTIME
import net.postchain.base.configuration.KEY_BLOCKSTRATEGY_MAXBLOCKSIZE
import net.postchain.base.configuration.KEY_BLOCKSTRATEGY_MAXBLOCKTIME
import net.postchain.base.configuration.KEY_BLOCKSTRATEGY_MAXBLOCKTRANSACTIONS
import net.postchain.base.configuration.KEY_BLOCKSTRATEGY_MAXSPECIALENDTRANSACTIONSIZE
import net.postchain.base.configuration.KEY_BLOCKSTRATEGY_MAXTXDELAY
import net.postchain.base.configuration.KEY_BLOCKSTRATEGY_MINBACKOFFTIME
import net.postchain.base.configuration.KEY_BLOCKSTRATEGY_MININTERBLOCKINTERVAL
import net.postchain.base.configuration.KEY_BLOCKSTRATEGY_PREEMPTIVEBLOCKBUILDING
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.RawGtv
import net.postchain.gtv.mapper.toObject

data class BaseBlockBuildingStrategyConfigurationData(
        @param:RawGtv
        val rawGtv: Gtv,
        @param:Name(KEY_BLOCKSTRATEGY_MAXBLOCKSIZE)
        @param:DefaultValue(defaultLong = (26 * 1024 * 1024)) // default is 26 MiB)
        val maxBlockSize: Long,
        @param:Name(KEY_BLOCKSTRATEGY_MAXBLOCKTRANSACTIONS)
        @param:DefaultValue(defaultLong = 100)
        val maxBlockTransactions: Long,
        @param:Name(KEY_BLOCKSTRATEGY_MININTERBLOCKINTERVAL)
        @param:DefaultValue(defaultLong = 25)
        val minInterBlockInterval: Long,
        @param:Name(KEY_BLOCKSTRATEGY_MAXBLOCKTIME)
        @param:DefaultValue(defaultLong = 30000) // 30 sec
        val maxBlockTime: Long,
        @param:Name(KEY_BLOCKSTRATEGY_MAXTXDELAY)
        @param:DefaultValue(defaultLong = 1000) // 1 sec
        val maxTxDelay: Long,
        @param:Name(KEY_BLOCKSTRATEGY_MINBACKOFFTIME)
        @param:DefaultValue(defaultLong = 20) // 20 ms
        val minBackoffTime: Long,
        @param:Name(KEY_BLOCKSTRATEGY_MAXBACKOFFTIME)
        @param:DefaultValue(defaultLong = 2000) // 2 sec
        val maxBackoffTime: Long,
        @param:Name(KEY_BLOCKSTRATEGY_MAXSPECIALENDTRANSACTIONSIZE)
        @param:DefaultValue(defaultLong = 1024) // Bytes
        val maxSpecialEndTransactionSize: Long,
        @param:Name(KEY_BLOCKSTRATEGY_PREEMPTIVEBLOCKBUILDING)
        @param:DefaultValue(defaultBoolean = true)
        val preemptiveBlockBuilding: Boolean,
) {
    companion object {
        @JvmStatic
        val default = gtv(mapOf()).toObject<BaseBlockBuildingStrategyConfigurationData>()
    }
}

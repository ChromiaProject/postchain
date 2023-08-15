package net.postchain.base

import net.postchain.base.configuration.*
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.RawGtv
import net.postchain.gtv.mapper.toObject

data class BaseBlockBuildingStrategyConfigurationData(
        @RawGtv
        val rawGtv: Gtv,
        @Name(KEY_BLOCKSTRATEGY_MAXBLOCKSIZE)
        @DefaultValue(defaultLong = (26 * 1024 * 1024)) // default is 26 MiB)
        val maxBlockSize: Long,
        @Name(KEY_BLOCKSTRATEGY_MAXBLOCKTRANSACTIONS)
        @DefaultValue(defaultLong = 100)
        val maxBlockTransactions: Long,
        @Name(KEY_BLOCKSTRATEGY_MININTERBLOCKINTERVAL)
        @DefaultValue(defaultLong = 25)
        val minInterBlockInterval: Long,
        @Name(KEY_BLOCKSTRATEGY_MAXBLOCKTIME)
        @DefaultValue(defaultLong = 30000) // 30 sec
        val maxBlockTime: Long,
        @Name(KEY_BLOCKSTRATEGY_MAXTXDELAY)
        @DefaultValue(defaultLong = 1000) // 1 sec
        val maxTxDelay: Long,
        @Name(KEY_BLOCKSTRATEGY_MINBACKOFFTIME)
        @DefaultValue(defaultLong = 20) // 20 ms
        val minBackoffTime: Long,
        @Name(KEY_BLOCKSTRATEGY_MAXBACKOFFTIME)
        @DefaultValue(defaultLong = 2000) // 2 sec
        val maxBackoffTime: Long,
        @Name(KEY_BLOCKSTRATEGY_MAXSPECIALENDTRANSACTIONSIZE)
        @DefaultValue(defaultLong = 1024) // Bytes
        val maxSpecialEndTransactionSize: Long,
        @Name(KEY_BLOCKSTRATEGY_PREEMPTIVEBLOCKBUILDING)
        @DefaultValue(defaultBoolean = true)
        val preemptiveBlockBuilding: Boolean,
) {
    companion object {
        @JvmStatic
        val default = gtv(mapOf()).toObject<BaseBlockBuildingStrategyConfigurationData>()
    }
}

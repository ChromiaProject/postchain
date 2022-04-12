package net.postchain.base.config

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary

interface GtvExtractable {
    infix fun from(gtv: GtvDictionary): Gtv?
}

enum class BlockchainConfigKeys(val key: String): GtvExtractable {
    SyncInfra("sync"),
    SyncInfraExt("sync_ext"),
    BlockStrategy("blockstrategy"),

    ConfigurationFactory("configurationfactory"),
    Signers("signers"),
    ChainDependencies("dependencies"),
    HistoricBrid("historic_brid"),

    Gtx("gtx");

    override fun from(gtv: GtvDictionary) = gtv[key]


    companion object {
        @JvmStatic
        fun fromKey(key: String): BlockchainConfigKeys? {
            return values().firstOrNull { it.key == key.toLowerCase() }
        }
    }
}

enum class BlockStrategyKeys(val key: String): GtvExtractable {
    BlockStrategyClass("name"),
    MaxBlockSize("maxblocksize"),
    MaxBlockTransactions("maxblocktransactions"),
    MaxBlockTime("maxblocktime"),
    MaxTxDelay("maxtxdelay"),
    MinInterBlockInterval("mininterblockinterval"),
    QueueCapacity("queuecapacity");

    override fun from(gtv: GtvDictionary): Gtv? {
        return gtv[BlockchainConfigKeys.BlockStrategy.key]?.let { it[key] }
    }
}

enum class GtxConfigKeys(val key: String): GtvExtractable {
    GtxModules("modules"),
    GtxMaxTxSize("max_transaction_size"),
    GtxDontAllowOverrides("allowoverrides");

    override fun from(gtv: GtvDictionary): Gtv? {
        return gtv[BlockchainConfigKeys.Gtx.key]?.let { it[key] }
    }
}
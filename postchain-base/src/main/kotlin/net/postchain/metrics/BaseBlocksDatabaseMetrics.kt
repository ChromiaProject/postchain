package net.postchain.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import net.postchain.common.BlockchainRid
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG

class BaseBlocksDatabaseMetrics(chainIID: Long, blockchainRid: BlockchainRid) {
    val signedBlocks: Timer = Timer.builder("signedBlocks")
        .description("Signed blocks")
        .tag(CHAIN_IID_TAG, chainIID.toString())
        .tag(BLOCKCHAIN_RID_TAG, blockchainRid.toHex())
        .register(Metrics.globalRegistry)

    val confirmedBlocks: Timer = Timer.builder("confirmedBlocks")
        .description("Confirmed blocks")
        .tag(CHAIN_IID_TAG, chainIID.toString())
        .tag(BLOCKCHAIN_RID_TAG, blockchainRid.toHex())
        .register(Metrics.globalRegistry)

    val confirmedTransactions: Counter = Counter.builder("confirmedTransactions")
        .description("Confirmed transactions")
        .tag(CHAIN_IID_TAG, chainIID.toString())
        .tag(BLOCKCHAIN_RID_TAG, blockchainRid.toHex())
        .register(Metrics.globalRegistry)
}

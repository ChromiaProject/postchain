package net.postchain.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import net.postchain.common.BlockchainRid
import net.postchain.core.TransactionQueue
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.logging.RESULT_TAG

private const val PROCESSED_METRIC_NAME = "processed.transactions"
private const val PROCESSED_METRIC_DESCRIPTION = "Transactions processed"

class BaseBlockchainEngineMetrics(chainIID: Long, blockchainRid: BlockchainRid, transactionQueue: TransactionQueue) {
    init {
        Gauge.builder("transaction.queue.size", transactionQueue) { transactionQueue.getTransactionQueueSize().toDouble() }
            .description("Transaction queue size")
            .tag(CHAIN_IID_TAG, chainIID.toString())
            .tag(BLOCKCHAIN_RID_TAG, blockchainRid.toHex())
            .register(Metrics.globalRegistry)
    }

    val acceptedTransactions: Timer = Timer.builder(PROCESSED_METRIC_NAME)
        .description(PROCESSED_METRIC_DESCRIPTION)
        .tag(CHAIN_IID_TAG, chainIID.toString())
        .tag(BLOCKCHAIN_RID_TAG, blockchainRid.toHex())
        .tag(RESULT_TAG, "ACCEPTED")
        .register(Metrics.globalRegistry)

    val rejectedTransactions: Timer = Timer.builder(PROCESSED_METRIC_NAME)
        .description(PROCESSED_METRIC_DESCRIPTION)
        .tag(CHAIN_IID_TAG, chainIID.toString())
        .tag(BLOCKCHAIN_RID_TAG, blockchainRid.toHex())
        .tag(RESULT_TAG, "REJECTED")
        .register(Metrics.globalRegistry)

    val blocks: Timer = Timer.builder("blocks")
        .description("Built blocks")
        .tag(CHAIN_IID_TAG, chainIID.toString())
        .tag(BLOCKCHAIN_RID_TAG, blockchainRid.toHex())
        .register(Metrics.globalRegistry)
}

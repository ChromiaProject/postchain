package net.postchain.base

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import net.postchain.common.BlockchainRid
import net.postchain.core.TransactionQueue

private const val PROCESSED_METRIC_NAME = "processed.transactions"
private const val PROCESSED_METRIC_DESCRIPTION = "Transactions processed"

class BaseBlockchainEngineMetrics(chainIID: Long, blockchainRid: BlockchainRid, transactionQueue: TransactionQueue) {
    init {
        Gauge.builder("transaction.queue.size", transactionQueue) { transactionQueue.getTransactionQueueSize().toDouble() }
            .description("Transaction queue size")
            .tag("chainIID", chainIID.toString())
            .tag("blockchainRID", blockchainRid.toHex())
            .register(Metrics.globalRegistry)
    }

    val acceptedTransactions: Timer = Timer.builder(PROCESSED_METRIC_NAME)
        .description(PROCESSED_METRIC_DESCRIPTION)
        .tag("chainIID", chainIID.toString())
        .tag("blockchainRID", blockchainRid.toHex())
        .tag("result", "ACCEPTED")
        .register(Metrics.globalRegistry)

    val rejectedTransactions: Timer = Timer.builder(PROCESSED_METRIC_NAME)
        .description(PROCESSED_METRIC_DESCRIPTION)
        .tag("chainIID", chainIID.toString())
        .tag("blockchainRID", blockchainRid.toHex())
        .tag("result", "REJECTED")
        .register(Metrics.globalRegistry)

    val blocks: Timer = Timer.builder("blocks")
        .description("Built blocks")
        .tag("chainIID", chainIID.toString())
        .tag("blockchainRID", blockchainRid.toHex())
        .register(Metrics.globalRegistry)
}

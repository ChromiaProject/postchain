package net.postchain.api.rest.controller

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import net.postchain.common.BlockchainRid
import net.postchain.common.tx.EnqueueTransactionResult

private const val SUBMITTED_METRIC_NAME = "submitted.transactions"
private const val SUBMITTED_METRIC_DESCRIPTION = "Transactions submitted/enqueued"

class PostchainModelMetrics(chainIID: Long, blockchainRid: BlockchainRid) {
     val fullTransactions: Timer = Timer.builder(SUBMITTED_METRIC_NAME)
        .description(SUBMITTED_METRIC_DESCRIPTION)
        .tag("chainIID", chainIID.toString())
        .tag("blockchainRID", blockchainRid.toHex())
        .tag("result", EnqueueTransactionResult.FULL.name)
        .register(Metrics.globalRegistry)

     val invalidTransactions: Timer = Timer.builder(SUBMITTED_METRIC_NAME)
        .description(SUBMITTED_METRIC_DESCRIPTION)
        .tag("chainIID", chainIID.toString())
        .tag("blockchainRID", blockchainRid.toHex())
        .tag("result", EnqueueTransactionResult.INVALID.name)
        .register(Metrics.globalRegistry)

     val duplicateTransactions: Timer = Timer.builder(SUBMITTED_METRIC_NAME)
        .description(SUBMITTED_METRIC_DESCRIPTION)
        .tag("chainIID", chainIID.toString())
        .tag("blockchainRID", blockchainRid.toHex())
        .tag("result", EnqueueTransactionResult.DUPLICATE.name)
        .register(Metrics.globalRegistry)

     val unknownTransactions: Timer = Timer.builder(SUBMITTED_METRIC_NAME)
        .description(SUBMITTED_METRIC_DESCRIPTION)
        .tag("chainIID", chainIID.toString())
        .tag("blockchainRID", blockchainRid.toHex())
        .tag("result", EnqueueTransactionResult.UNKNOWN.name)
        .register(Metrics.globalRegistry)

     val okTransactions: Timer = Timer.builder(SUBMITTED_METRIC_NAME)
        .description(SUBMITTED_METRIC_DESCRIPTION)
        .tag("chainIID", chainIID.toString())
        .tag("blockchainRID", blockchainRid.toHex())
        .tag("result", EnqueueTransactionResult.OK.name)
        .register(Metrics.globalRegistry)
}

package net.postchain.metrics

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import net.postchain.common.BlockchainRid
import net.postchain.common.tx.EnqueueTransactionResult
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.logging.RESULT_TAG

private const val SUBMITTED_METRIC_NAME = "submitted.transactions"
private const val SUBMITTED_METRIC_DESCRIPTION = "Transactions submitted/enqueued"

class PostchainModelMetrics(chainIID: Long, blockchainRid: BlockchainRid) {
     val fullTransactions: Timer = Timer.builder(SUBMITTED_METRIC_NAME)
        .description(SUBMITTED_METRIC_DESCRIPTION)
        .tag(CHAIN_IID_TAG, chainIID.toString())
        .tag(BLOCKCHAIN_RID_TAG, blockchainRid.toHex())
        .tag(RESULT_TAG, EnqueueTransactionResult.FULL.name)
        .register(Metrics.globalRegistry)

     val invalidTransactions: Timer = Timer.builder(SUBMITTED_METRIC_NAME)
        .description(SUBMITTED_METRIC_DESCRIPTION)
        .tag(CHAIN_IID_TAG, chainIID.toString())
        .tag(BLOCKCHAIN_RID_TAG, blockchainRid.toHex())
        .tag(RESULT_TAG, EnqueueTransactionResult.INVALID.name)
        .register(Metrics.globalRegistry)

     val duplicateTransactions: Timer = Timer.builder(SUBMITTED_METRIC_NAME)
        .description(SUBMITTED_METRIC_DESCRIPTION)
        .tag(CHAIN_IID_TAG, chainIID.toString())
        .tag(BLOCKCHAIN_RID_TAG, blockchainRid.toHex())
        .tag(RESULT_TAG, EnqueueTransactionResult.DUPLICATE.name)
        .register(Metrics.globalRegistry)

     val okTransactions: Timer = Timer.builder(SUBMITTED_METRIC_NAME)
        .description(SUBMITTED_METRIC_DESCRIPTION)
        .tag(CHAIN_IID_TAG, chainIID.toString())
        .tag(BLOCKCHAIN_RID_TAG, blockchainRid.toHex())
        .tag(RESULT_TAG, EnqueueTransactionResult.OK.name)
        .register(Metrics.globalRegistry)
}

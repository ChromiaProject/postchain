package net.postchain.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import net.postchain.common.BlockchainRid
import net.postchain.ebft.NodeBlockState
import net.postchain.ebft.NodeBlockState.HaveBlock
import net.postchain.ebft.NodeBlockState.Prepared
import net.postchain.ebft.NodeBlockState.WaitBlock
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.NODE_BLOCK_STATE_TAG
import net.postchain.logging.CHAIN_IID_TAG

internal const val VALIDATOR_FAST_SYNC_SWITCH_METRIC_NAME = "validatorFastSyncSwitch"
internal const val VALIDATOR_FAST_SYNC_SWITCH_METRIC_DESCRIPTION = "Number of fast syncs started by validator"

class SyncMetrics(val chainIID: Long, val blockchainRid: BlockchainRid) {

    val validatorFastSyncSwitchWaitBlock: Counter = createStateCounter(WaitBlock)
    val validatorFastSyncSwitchHaveBlock: Counter = createStateCounter(HaveBlock)
    val validatorFastSyncSwitchPrepared: Counter = createStateCounter(Prepared)

    private fun createStateCounter(state: NodeBlockState): Counter =
            Counter.builder(VALIDATOR_FAST_SYNC_SWITCH_METRIC_NAME)
                    .description(VALIDATOR_FAST_SYNC_SWITCH_METRIC_DESCRIPTION)
                    .tag(CHAIN_IID_TAG, chainIID.toString())
                    .tag(BLOCKCHAIN_RID_TAG, blockchainRid.toHex())
                    .tag(NODE_BLOCK_STATE_TAG, state.name)
                    .register(Metrics.globalRegistry)
}
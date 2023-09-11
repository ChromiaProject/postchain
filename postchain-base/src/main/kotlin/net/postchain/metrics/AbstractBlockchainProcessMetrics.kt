package net.postchain.metrics

import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Metrics
import net.postchain.common.BlockchainRid
import net.postchain.core.framework.AbstractBlockchainProcess
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import java.io.Closeable

class AbstractBlockchainProcessMetrics(chainIID: Long, blockchainRid: BlockchainRid, abstractBlockchainProcess: AbstractBlockchainProcess) : Closeable {
    private val blockHeightMetric: Meter = FunctionCounter.builder("blockHeight", abstractBlockchainProcess) { abstractBlockchainProcess.currentBlockHeight().toDouble() }
            .description("Current block height")
            .tag(CHAIN_IID_TAG, chainIID.toString())
            .tag(BLOCKCHAIN_RID_TAG, blockchainRid.toHex())
            .register(Metrics.globalRegistry)

    override fun close() {
        Metrics.globalRegistry.remove(blockHeightMetric)
    }
}

package net.postchain.base.l2

import net.postchain.base.BaseBlockchainConfigurationData
import net.postchain.common.data.KECCAK256
import net.postchain.core.BlockBuilder
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.crypto.EthereumL2DigestSystem
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.gtx.GTXModule

class L2BlockchainConfiguration(configData: BaseBlockchainConfigurationData, module: GTXModule): GTXBlockchainConfiguration(configData, module) {

    override fun makeBlockBuilder(ctx: EContext): BlockBuilder {
        addChainIDToDependencies(ctx)
        val l2Implementation = EthereumL2Implementation(EthereumL2DigestSystem(KECCAK256))
        return L2BlockBuilder(
            effectiveBlockchainRID,
            cryptoSystem,
            ctx,
            blockStore,
            getTransactionFactory(),
            getSpecialTxHandler(),
            signers.toTypedArray(),
            configData.blockSigMaker,
            bcRelatedInfosDependencyList,
            effectiveBlockchainRID != blockchainRid,
            l2Implementation,
            configData.getMaxBlockSize(),
            configData.getMaxBlockTransactions()
        )
    }
}

class L2BlockchainConfigurationFactory: GTXBlockchainConfigurationFactory() {
    override fun makeBlockchainConfiguration(configurationData: Any): BlockchainConfiguration {
        val cfData = configurationData as BaseBlockchainConfigurationData
        val effectiveBRID = cfData.getHistoricBRID() ?: configurationData.context.blockchainRID
        return L2BlockchainConfiguration(
            cfData,
            createGtxModule(effectiveBRID, configurationData.data))
    }
}
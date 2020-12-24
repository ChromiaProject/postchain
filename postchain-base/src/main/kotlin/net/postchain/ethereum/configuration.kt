package net.postchain.ethereum

import net.postchain.base.BaseBlockchainConfigurationData
import net.postchain.base.NullSpecialTransactionHandler
import net.postchain.base.SpecialTransactionHandler
import net.postchain.core.BlockBuilder
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.gtx.GTXModule

class L2BlockchainConfiguration(configData: BaseBlockchainConfigurationData, module: GTXModule): GTXBlockchainConfiguration(configData, module) {

    override fun getSpecialTxHandler(): SpecialTransactionHandler {
        // TODO: Need to implement special tnx handler for L2 Block Builder
        return NullSpecialTransactionHandler()
    }

    override fun makeBlockBuilder(ctx: EContext): BlockBuilder {
        addChainIDToDependencies(ctx)
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
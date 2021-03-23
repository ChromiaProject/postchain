package net.postchain.base.l2

import net.postchain.base.BaseBlockchainConfigurationData
import net.postchain.base.SpecialTransactionHandler
import net.postchain.common.data.KECCAK256
import net.postchain.core.BlockBuilder
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.crypto.EthereumL2DigestSystem
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXTransactionFactory
import net.postchain.l2.L2SpecialTxHandler

class L2BlockchainConfiguration(configData: BaseBlockchainConfigurationData, module: GTXModule): GTXBlockchainConfiguration(configData, module) {

    private val txFactory = GTXTransactionFactory(
        effectiveBlockchainRID, module, cryptoSystem, configData.getMaxTransactionSize())
    private val specTxHandler = L2SpecialTxHandler(module, effectiveBlockchainRID, cryptoSystem, txFactory)

    override fun makeBlockBuilder(ctx: EContext): BlockBuilder {
        addChainIDToDependencies(ctx)
        val levelsPerPage = configData.getLayer2()?.get("levels_per_page")?.asInteger() ?: 2
        val l2Implementation = EthereumL2Implementation(EthereumL2DigestSystem(KECCAK256), levelsPerPage.toInt())
        val bb = L2BlockBuilder(
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
        bb.installEventProcessor("l2_event", l2Implementation.eventProc)
        bb.installEventProcessor("l2_state", l2Implementation.stateProc)
        return bb
    }

    override fun getSpecialTxHandler(): SpecialTransactionHandler {
        return specTxHandler
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
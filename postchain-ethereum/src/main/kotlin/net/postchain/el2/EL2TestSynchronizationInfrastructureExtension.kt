// Copyright (c) 2021 ChromaWay AB. See README for license information.
package net.postchain.el2

import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.*
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.gtx.GTXBlockchainConfiguration

class EL2TestSynchronizationInfrastructureExtension(
    nodeConfigProvider: NodeConfigurationProvider,
    nodeDiagnosticContext: NodeDiagnosticContext
) : SynchronizationInfrastructureExtension {

    override fun connectProcess(process: BlockchainProcess) {
        val engine = process.blockchainEngine
        val proc = L2TestEventProcessor()
        val gtxModule = (engine.getConfiguration() as GTXBlockchainConfiguration).module
        val txExtensions = gtxModule.getSpecialTxExtensions()
        for (te in txExtensions) {
            if (te is EL2SpecialTxExtension) te.useEventProcessor(proc)
        }
    }

    override fun shutdown() {}
}

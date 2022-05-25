// Copyright (c) 2021 ChromaWay AB. See README for license information.
package net.postchain.eif

import net.postchain.PostchainContext
import net.postchain.core.*
import net.postchain.gtx.GTXBlockchainConfiguration

class EifTestSynchronizationInfrastructureExtension(
    postchainContext: PostchainContext
) : SynchronizationInfrastructureExtension {

    override fun connectProcess(process: BlockchainProcess) {
        val engine = process.blockchainEngine
        val proc = EifTestEventProcessor()
        val gtxModule = (engine.getConfiguration() as GTXBlockchainConfiguration).module
        val txExtensions = gtxModule.getSpecialTxExtensions()
        for (te in txExtensions) {
            if (te is EifSpecialTxExtension) te.useEventProcessor(proc)
        }
    }

    override fun disconnectProcess(process: BlockchainProcess) {}

    override fun shutdown() {}
}

package net.postchain.core.block

import net.postchain.common.BlockchainRid
import net.postchain.core.BlockchainProcessManager

class BlockQueriesProviderImpl : BlockQueriesProvider {
    lateinit var processManager: BlockchainProcessManager

    override fun getBlockQueries(blockchainRid: BlockchainRid): BlockQueries? =
            processManager.retrieveBlockchain(blockchainRid)?.blockchainEngine?.getBlockQueries()
}
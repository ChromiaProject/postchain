package net.postchain.server.service

import mu.KLogging
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.core.Storage
import net.postchain.crypto.PrivKey
import net.postchain.server.LazyPostchainNodeProvider

class SubnodeService(private val nodeProvider: LazyPostchainNodeProvider) {

    companion object : KLogging()

    private val sharedStorage: Storage
        get() = nodeProvider.get().postchainContext.sharedStorage

    fun initNode(privKey: PrivKey) {
        nodeProvider.init(privKey, false)
    }

    fun startBlockchain(chainId: Long, blockchainRid: BlockchainRid) {
        // If the chain is already running then this is a restart request
        // We don't need to init the blockchain and if we do we risk deadlocking
        if (!nodeProvider.get().isBlockchainRunning(chainId)) {
            logger.info("Initializing blockchain $chainId")
            withWriteConnection(sharedStorage, chainId) {
                val db = DatabaseAccess.of(it)
                db.initializeBlockchain(it, blockchainRid)
                true
            }
        }
        logger.info("Starting blockchain $chainId")
        nodeProvider.get().startBlockchain(chainId)
        logger.info("Started blockchain $chainId")
    }
}
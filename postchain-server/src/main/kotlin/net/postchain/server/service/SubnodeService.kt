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

    val storage: Storage
        get() = nodeProvider.get().postchainContext.storage

    fun initNode(privKey: PrivKey) {
        nodeProvider.init(privKey, false)
    }

    fun startBlockchain(chainId: Long, blockchainRid: BlockchainRid) {
        logger.info("Initializing blockchain $chainId")
        withWriteConnection(storage, chainId) {
            val db = DatabaseAccess.of(it)
            db.initializeBlockchain(it, blockchainRid)
            true
        }
        logger.info("Starting blockchain $chainId")
        nodeProvider.get().startBlockchain(chainId)
        logger.info("Started blockchain $chainId")
    }
}
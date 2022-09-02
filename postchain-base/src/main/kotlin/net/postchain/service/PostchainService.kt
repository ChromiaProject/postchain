package net.postchain.service

import net.postchain.PostchainNode
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DependenciesValidator
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.AlreadyExists
import net.postchain.common.exception.NotFound
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder

class PostchainService(private val postchainNode: PostchainNode) {
    fun startBlockchain(chainId: Long): BlockchainRid {
        return postchainNode.startBlockchain(chainId)
    }

    fun stopBlockchain(chainId: Long) {
        postchainNode.stopBlockchain(chainId)
    }

    fun addConfiguration(chainId: Long, height: Long, override: Boolean, config: Gtv) {
        withWriteConnection(postchainNode.postchainContext.storage, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            val hasConfig = db.getConfigurationData(ctx, height) != null
            if (hasConfig && !override) {
                throw AlreadyExists("Configuration already exists for height $height on chain $chainId")
            }
            db.addConfigurationData(ctx, height, GtvEncoder.encodeGtv(config))
            true
        }
    }

    fun initializeBlockchain(chainId: Long, maybeBrid: BlockchainRid?, override: Boolean, config: Gtv): BlockchainRid {
        withWriteConnection(postchainNode.postchainContext.storage, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            if (db.getBlockchainRid(ctx) != null && !override) {
                throw AlreadyExists("Blockchain already exists")
            }

            val brid = maybeBrid ?: GtvToBlockchainRidFactory.calculateBlockchainRid(config)

            db.initializeBlockchain(ctx, brid)
            DependenciesValidator.validateBlockchainRids(ctx, listOf())
            // TODO: Blockchain dependencies [DependenciesValidator#validateBlockchainRids]
            db.addConfigurationData(ctx, 0, GtvEncoder.encodeGtv(config))
            true
        }
        return postchainNode.startBlockchain(chainId)
    }

    fun findBlockchain(chainId: Long): Pair<BlockchainRid, Boolean>? {
        val brid = withReadConnection(postchainNode.postchainContext.storage, chainId) {
            DatabaseAccess.of(it).getBlockchainRid(it)
        }
        return if (brid != null) Pair(brid, postchainNode.isBlockchainRunning(chainId)) else null
    }

    fun addBlockchainReplica(brid: String, pubkey: String) {
        postchainNode.postchainContext.storage.withWriteConnection { ctx ->
            val db = DatabaseAccess.of(ctx)

            val foundInPeerInfo = db.findPeerInfo(ctx, null, null, pubkey)
            if (foundInPeerInfo.isEmpty()) {
                throw NotFound("Given pubkey is not a peer. First add it as a peer.")
            }

            db.addBlockchainReplica(ctx, brid, pubkey)
            true
        }
    }

    fun removeBlockchainReplica(brid: String, pubkey: String): Set<BlockchainRid> =
        postchainNode.postchainContext.storage.withWriteConnection { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.removeBlockchainReplica(ctx, brid, pubkey)
        }
}

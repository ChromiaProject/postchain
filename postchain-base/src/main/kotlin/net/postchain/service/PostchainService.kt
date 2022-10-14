package net.postchain.service

import net.postchain.PostchainNode
import net.postchain.base.BlockchainRelatedInfo
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DependenciesValidator
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.NotFound
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder

class PostchainService(private val postchainNode: PostchainNode) {
    fun startBlockchain(chainId: Long): BlockchainRid = postchainNode.startBlockchain(chainId)

    fun stopBlockchain(chainId: Long) {
        postchainNode.stopBlockchain(chainId)
    }

    /**
     * @return `true` if configuration was added, `false` if already existed and `override` is `false`
     * @throws IllegalStateException if current height => given height
     */
    fun addConfiguration(chainId: Long, height: Long, override: Boolean, config: Gtv): Boolean =
            withWriteConnection(postchainNode.postchainContext.storage, chainId) { ctx ->
                val db = DatabaseAccess.of(ctx)

                val lastBlockHeight = db.getLastBlockHeight(ctx)
                if (lastBlockHeight >= height) {
                    throw IllegalStateException("Cannot add configuration at $height, since last block is already at $lastBlockHeight")
                }

                if (override || db.getConfigurationData(ctx, height) == null) {
                    db.addConfigurationData(ctx, height, GtvEncoder.encodeGtv(config))
                    true
                } else {
                    false
                }
            }

    /**
     * @return [BlockchainRid] if chain was initialized, `null` if already existed and `override` is `false`
     */
    fun initializeBlockchain(chainId: Long, maybeBrid: BlockchainRid?, override: Boolean, config: Gtv,
                             givenDependencies: List<BlockchainRelatedInfo> = listOf()): BlockchainRid? {
        val brid = maybeBrid
                ?: GtvToBlockchainRidFactory.calculateBlockchainRid(config, postchainNode.postchainContext.cryptoSystem)

        val initialized = withWriteConnection(postchainNode.postchainContext.storage, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)

            if (override || db.getBlockchainRid(ctx) == null) {
                db.initializeBlockchain(ctx, brid)
                DependenciesValidator.validateBlockchainRids(ctx, givenDependencies)
                // TODO: Blockchain dependencies [DependenciesValidator#validateBlockchainRids]
                db.addConfigurationData(ctx, 0, GtvEncoder.encodeGtv(config))
                true
            } else {
                false
            }
        }

        return if (initialized) brid else null
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

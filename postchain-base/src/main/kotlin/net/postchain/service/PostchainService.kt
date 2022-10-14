package net.postchain.service

import net.postchain.PostchainNode
import net.postchain.base.BlockchainRelatedInfo
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

    /**
     * @return true if configuration was added, false otherwise
     * @throws AlreadyExists if already existed and mode is ERROR
     */
    fun addConfiguration(chainId: Long, height: Long, mode: AlreadyExistMode, config: Gtv): Boolean =
            withWriteConnection(postchainNode.postchainContext.storage, chainId) { ctx ->
                val db = DatabaseAccess.of(ctx)

                when (mode) {
                    AlreadyExistMode.ERROR ->
                        if (db.getConfigurationData(ctx, height) == null) {
                            db.addConfigurationData(ctx, height, GtvEncoder.encodeGtv(config))
                            true
                        } else {
                            throw AlreadyExists("Configuration already exists for height $height on chain $chainId")
                        }

                    AlreadyExistMode.FORCE -> {
                        db.addConfigurationData(ctx, height, GtvEncoder.encodeGtv(config))
                        true
                    }

                    AlreadyExistMode.IGNORE ->
                        if (db.getConfigurationData(ctx, height) == null) {
                            db.addConfigurationData(ctx, height, GtvEncoder.encodeGtv(config))
                            true
                        } else {
                            false
                        }
                }
        }

    fun initializeBlockchain(chainId: Long, maybeBrid: BlockchainRid?, mode: AlreadyExistMode, config: Gtv,
                             givenDependencies: List<BlockchainRelatedInfo> = listOf()): BlockchainRid {
        val brid = maybeBrid ?: GtvToBlockchainRidFactory.calculateBlockchainRid(config, postchainNode.postchainContext.cryptoSystem)

        withWriteConnection(postchainNode.postchainContext.storage, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)

            fun init() {
                db.initializeBlockchain(ctx, brid)
                DependenciesValidator.validateBlockchainRids(ctx, givenDependencies)
                db.addConfigurationData(ctx, 0, GtvEncoder.encodeGtv(config))
            }

            when (mode) {
                AlreadyExistMode.ERROR -> {
                    if (db.getBlockchainRid(ctx) == null) {
                        init()
                    } else {
                        throw AlreadyExists("Blockchain already exists")
                    }
                }

                AlreadyExistMode.FORCE -> {
                    init()
                }

                AlreadyExistMode.IGNORE -> {
                    db.getBlockchainRid(ctx) ?: init()
                }
            }
            true
        }

        return brid
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

package net.postchain.service

import net.postchain.PostchainNode
import net.postchain.base.BlockchainRelatedInfo
import net.postchain.base.configuration.KEY_SIGNERS
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DependenciesValidator
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.NotFound
import net.postchain.common.toHex
import net.postchain.core.BadDataMistake
import net.postchain.core.BadDataType
import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvEncoder

class PostchainService(private val postchainNode: PostchainNode) {
    fun startBlockchain(chainId: Long): BlockchainRid = postchainNode.startBlockchain(chainId)

    fun stopBlockchain(chainId: Long) {
        postchainNode.stopBlockchain(chainId)
    }

    /**
     * @return `true` if configuration was added, `false` if already existed and `override` is `false`
     * @throws IllegalStateException if current height => given height
     * @throws BadDataMistake if signer pubkey does not exist in peerinfos and allowUnknownSigners is false
     */
    fun addConfiguration(chainId: Long, height: Long, override: Boolean, config: Gtv, allowUnknownSigners: Boolean = false): Boolean =
            withWriteConnection(postchainNode.postchainContext.storage, chainId) { ctx ->
                val db = DatabaseAccess.of(ctx)

                val lastBlockHeight = db.getLastBlockHeight(ctx)
                if (lastBlockHeight >= height) {
                    throw IllegalStateException("Cannot add configuration at $height, since last block is already at $lastBlockHeight")
                }

                if (override || db.getConfigurationData(ctx, height) == null) {
                    db.addConfigurationData(ctx, height, GtvEncoder.encodeGtv(config))
                    addFutureSignersAsReplicas(ctx, db, height, config, allowUnknownSigners)
                    true
                } else {
                    false
                }
            }


    /** When a new (height > 0) configuration is added, we automatically add signers in that config to table
     * blockchainReplicaNodes (for current blockchain). Useful for synchronization.
     */
    private fun addFutureSignersAsReplicas(eContext: EContext, db: DatabaseAccess, height: Long, gtvData: Gtv, allowUnknownSigners: Boolean) {
        if (height > 0) {
            val brid = db.getBlockchainRid(eContext)!!
            val confGtvDict = gtvData as GtvDictionary
            val signers = confGtvDict[KEY_SIGNERS]!!.asArray().map { it.asByteArray() }
            for (sig in signers) {
                val nodePubkey = sig.toHex()
                // Node must be in PeerInfo, or else it cannot be a blockchain replica.
                val foundInPeerInfo = db.findPeerInfo(eContext, null, null, nodePubkey)
                if (foundInPeerInfo.isNotEmpty()) {
                    db.addBlockchainReplica(eContext, brid.toHex(), nodePubkey)
                    // If the node is not in the peerinfo table and we do not allow unknown signers in a configuration,
                    // throw error
                } else if (!allowUnknownSigners) {
                    throw BadDataMistake(BadDataType.MISSING_PEERINFO, "Signer $nodePubkey does not exist in peerinfos.")
                }
            }
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

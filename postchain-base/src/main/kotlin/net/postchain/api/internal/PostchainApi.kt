package net.postchain.api.internal

import net.postchain.base.BlockchainRelatedInfo
import net.postchain.base.PeerInfo
import net.postchain.base.configuration.KEY_SIGNERS
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DependenciesValidator
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.AlreadyExists
import net.postchain.common.exception.NotFound
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.AppContext
import net.postchain.core.BadDataMistake
import net.postchain.core.BadDataType
import net.postchain.core.EContext
import net.postchain.crypto.PubKey
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvEncoder

object PostchainApi {

    /**
     * @return `true` if configuration was added, `false` if already existed and `override` is `false`
     * @throws IllegalStateException if current height => given height
     * @throws BadDataMistake if signer pubkey does not exist in peerinfos and allowUnknownSigners is false
     */
    fun addConfiguration(ctx: EContext, height: Long, override: Boolean, config: Gtv, allowUnknownSigners: Boolean = false): Boolean {
        val db = DatabaseAccess.of(ctx)

        val lastBlockHeight = db.getLastBlockHeight(ctx)
        if (lastBlockHeight >= height) {
            throw IllegalStateException("Cannot add configuration at $height, since last block is already at $lastBlockHeight")
        }

        return if (override || db.getConfigurationData(ctx, height) == null) {
            db.addConfigurationData(ctx, height, GtvEncoder.encodeGtv(config))
            addFutureSignersAsReplicas(ctx, db, height, config, allowUnknownSigners)
            true
        } else {
            false
        }
    }

    fun removeConfiguration(ctx: EContext, height: Long): Int =
            DatabaseAccess.of(ctx).removeConfiguration(ctx, height)

    fun checkBlockchain(ctx: EContext, blockchainRID: String) {
        val db = DatabaseAccess.of(ctx)

        val currentBrid = db.getBlockchainRid(ctx)
        when {
            currentBrid == null -> {
                throw UserMistake("Unknown chain-id: ${ctx.chainID}")
            }

            !blockchainRID.equals(currentBrid.toHex(), true) -> {
                throw UserMistake(
                        """
                    BlockchainRids are not equal:
                        expected: $blockchainRID
                        actual: ${currentBrid.toHex()}
                """.trimIndent()
                )
            }

            db.findConfigurationHeightForBlock(ctx, 0) == null -> {
                throw UserMistake("No configuration found")
            }

            else -> {
            }
        }
    }

    fun getConfiguration(ctx: EContext, height: Long): ByteArray? =
            DatabaseAccess.of(ctx).getConfigurationData(ctx, height)

    fun listConfigurations(ctx: EContext) =
            DatabaseAccess.of(ctx).listConfigurations(ctx)

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
                    // If the node is not in the peerinfo table, and we do not allow unknown signers in a configuration,
                    // throw error
                } else if (!allowUnknownSigners) {
                    throw BadDataMistake(BadDataType.MISSING_PEERINFO, "Signer $nodePubkey does not exist in peerinfos.")
                }
            }
        }
    }

    fun getLastBlockHeight(ctx: EContext) =
            DatabaseAccess.of(ctx).getLastBlockHeight(ctx)

    /**
     * @return `true` if chain was initialized, `false` if already existed and `override` is `false`
     */
    fun initializeBlockchain(ctx: EContext, brid: BlockchainRid, override: Boolean, config: Gtv, givenDependencies: List<BlockchainRelatedInfo> = listOf()): Boolean {
        val db = DatabaseAccess.of(ctx)

        return if (override || db.getBlockchainRid(ctx) == null) {
            db.initializeBlockchain(ctx, brid)
            DependenciesValidator.validateBlockchainRids(ctx, givenDependencies)
            // TODO: Blockchain dependencies [DependenciesValidator#validateBlockchainRids]
            db.addConfigurationData(ctx, 0, GtvEncoder.encodeGtv(config))
            true
        } else {
            false
        }
    }

    fun findBlockchain(ctx: EContext): BlockchainRid? = DatabaseAccess.of(ctx).getBlockchainRid(ctx)

    fun addBlockchainReplica(ctx: AppContext, brid: String, pubkey: String): Boolean {
        val db = DatabaseAccess.of(ctx)

        val foundInPeerInfo = db.findPeerInfo(ctx, null, null, pubkey)
        if (foundInPeerInfo.isEmpty()) {
            throw NotFound("Given pubkey is not a peer. First add it as a peer.")
        }

        return db.addBlockchainReplica(ctx, brid, pubkey)
    }

    fun removeBlockchainReplica(ctx: AppContext, brid: String?, pubkey: String): Set<BlockchainRid> =
            DatabaseAccess.of(ctx).removeBlockchainReplica(ctx, brid, pubkey)

    fun addPeer(ctx: AppContext, pubkey: PubKey, host: String, port: Int, override: Boolean): Boolean {
        val db = DatabaseAccess.of(ctx)
        val targetHost = db.findPeerInfo(ctx, host, port, null)
        if (targetHost.isNotEmpty()) {
            throw AlreadyExists("Peer already exists on current host with pubkey ${targetHost[0].pubKey.toHex()}")
        }
        val targetKey = db.findPeerInfo(ctx, null, null, pubkey.hex())
        return if (targetKey.isNotEmpty()) {
            if (override) {
                db.updatePeerInfo(ctx, host, port, pubkey.hex())
            } else {
                false
            }
        } else {
            db.addPeerInfo(ctx, host, port, pubkey.hex())
        }
    }

    fun addPeers(ctx: AppContext, peerInfos: Collection<PeerInfo>): Array<PeerInfo> {
        val db = DatabaseAccess.of(ctx)

        val imported = mutableListOf<PeerInfo>()
        peerInfos.forEach { peerInfo ->
            val noHostPort = db.findPeerInfo(ctx, peerInfo.host, peerInfo.port, null).isEmpty()
            val noPubKey = db.findPeerInfo(ctx, null, null, peerInfo.pubKey.toHex()).isEmpty()

            if (noHostPort && noPubKey) {
                val added = db.addPeerInfo(
                        ctx, peerInfo.host, peerInfo.port, peerInfo.pubKey.toHex(), peerInfo.lastUpdated)

                if (added) {
                    imported.add(peerInfo)
                }
            }
        }
        return imported.toTypedArray()
    }

    fun removePeer(ctx: AppContext, pubkey: PubKey): Array<PeerInfo> =
            DatabaseAccess.of(ctx).removePeerInfo(ctx, pubkey.hex())

    fun listPeers(ctx: AppContext): Array<PeerInfo> =
            DatabaseAccess.of(ctx).findPeerInfo(ctx, null, null, null)

    fun findPeerInfo(ctx: AppContext, host: String?, port: Int?, pubKeyPattern: String?) =
            DatabaseAccess.of(ctx).findPeerInfo(ctx, host, port, pubKeyPattern)

    fun setMustSyncUntil(ctx: AppContext, blockchainRID: BlockchainRid, height: Long): Boolean =
            DatabaseAccess.of(ctx).setMustSyncUntil(ctx, blockchainRID, height)

    fun getMustSyncUntilHeight(ctx: AppContext): Map<Long, Long> =
            DatabaseAccess.of(ctx).getMustSyncUntil(ctx)
}

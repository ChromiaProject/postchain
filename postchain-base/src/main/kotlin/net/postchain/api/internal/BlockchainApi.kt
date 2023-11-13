package net.postchain.api.internal

import net.postchain.base.BlockchainRelatedInfo
import net.postchain.base.configuration.KEY_SIGNERS
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DependenciesValidator
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.NotFound
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.AppContext
import net.postchain.core.EContext
import net.postchain.core.MissingPeerInfoException
import net.postchain.crypto.PubKey
import net.postchain.crypto.sha256Digest
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvEncoder
import net.postchain.gtx.GTXBlockchainConfigurationFactory

object BlockchainApi {

    /**
     * @return `true` if configuration was added, `false` if already existed and `override` is `false`
     * @throws IllegalStateException if current height => given height
     * @throws MissingPeerInfoException if signer pubkey does not exist in peerinfos and allowUnknownSigners is false
     */
    fun addConfiguration(ctx: EContext, height: Long, override: Boolean, config: Gtv, allowUnknownSigners: Boolean = false, validate: Boolean = true): Boolean {
        val db = DatabaseAccess.of(ctx)

        val blockchainRid = db.getBlockchainRid(ctx)
                ?: throw IllegalStateException("Blockchain with id ${ctx.chainID} not found")
        val lastBlockHeight = db.getLastBlockHeight(ctx)
        if (lastBlockHeight >= height) {
            throw IllegalStateException("Cannot add configuration at $height, since last block is already at $lastBlockHeight")
        }

        val configHash = GtvToBlockchainRidFactory.calculateBlockchainRid(config, ::sha256Digest)
        if (configurationExists(ctx, configHash.data)) {
            throw IllegalStateException("Configuration already exists: $configHash")
        }

        return if (override || db.getConfigurationData(ctx, height) == null) {
            if (validate) {
                GTXBlockchainConfigurationFactory.validateConfiguration(config, blockchainRid)
            }
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

    fun listConfigurationHashes(ctx: EContext) = DatabaseAccess.of(ctx).listConfigurationHashes(ctx)

    fun configurationExists(ctx: EContext, hash: ByteArray) = DatabaseAccess.of(ctx).configurationHashExists(ctx, hash)

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
                    db.addBlockchainReplica(eContext, brid, PubKey(nodePubkey))
                    // If the node is not in the peerinfo table, and we do not allow unknown signers in a configuration,
                    // throw error
                } else if (!allowUnknownSigners) {
                    throw MissingPeerInfoException("Signer $nodePubkey does not exist in peerinfos.")
                }
            }
        }
    }

    fun getLastBlockHeight(ctx: EContext) =
            DatabaseAccess.of(ctx).getLastBlockHeight(ctx)

    /**
     * @return `true` if chain was initialized, `false` if already existed and `override` is `false`
     */
    fun initializeBlockchain(ctx: EContext, brid: BlockchainRid, override: Boolean, config: Gtv, givenDependencies: List<BlockchainRelatedInfo> = listOf(), validate: Boolean = true): Boolean {
        val db = DatabaseAccess.of(ctx)

        return if (override || db.getBlockchainRid(ctx) == null) {
            db.initializeBlockchain(ctx, brid)
            DependenciesValidator.validateBlockchainRids(ctx, givenDependencies)
            // TODO: Blockchain dependencies [DependenciesValidator#validateBlockchainRids]
            if (validate) {
                GTXBlockchainConfigurationFactory.validateConfiguration(config, brid)
            }
            db.addConfigurationData(ctx, 0, GtvEncoder.encodeGtv(config))
            true
        } else {
            false
        }
    }

    fun findBlockchain(ctx: EContext): BlockchainRid? = DatabaseAccess.of(ctx).getBlockchainRid(ctx)

    fun addBlockchainReplica(ctx: AppContext, brid: BlockchainRid, pubkey: PubKey): Boolean {
        val db = DatabaseAccess.of(ctx)

        val foundInPeerInfo = db.findPeerInfo(ctx, null, null, pubkey.hex())
        if (foundInPeerInfo.isEmpty()) {
            throw NotFound("Given pubkey is not a peer. First add it as a peer.")
        }

        return db.addBlockchainReplica(ctx, brid, pubkey)
    }

    fun removeBlockchainReplica(ctx: AppContext, brid: BlockchainRid?, pubkey: PubKey): Set<BlockchainRid> =
            DatabaseAccess.of(ctx).removeBlockchainReplica(ctx, brid, pubkey)

    fun setMustSyncUntil(ctx: AppContext, blockchainRID: BlockchainRid, height: Long): Boolean =
            DatabaseAccess.of(ctx).setMustSyncUntil(ctx, blockchainRID, height)

    fun getMustSyncUntilHeight(ctx: AppContext): Map<Long, Long> =
            DatabaseAccess.of(ctx).getMustSyncUntil(ctx)

    fun getDependentChains(ctx: EContext): List<BlockchainRid> =
            DatabaseAccess.of(ctx).getDependenciesOnBlockchain(ctx)

    fun deleteBlockchain(ctx: EContext) {
        val db = DatabaseAccess.of(ctx)

        db.removeAllBlockchainSpecificFunctions(ctx)
        db.removeAllBlockchainSpecificTables(ctx)
        db.removeBlockchainFromMustSyncUntil(ctx)
        db.removeAllBlockchainReplicas(ctx)
        db.removeBlockchain(ctx)
    }

    fun archiveBlockchain(ctx: EContext) {
        val db = DatabaseAccess.of(ctx)
        db.removeAllBlockchainSpecificFunctions(ctx)
        db.removeAllBlockchainSpecificTables(ctx, listOf("configurations", "blocks", "transactions"))
    }

    fun isBlockchainArchivedOnNode(ctx: EContext): Boolean {
        val db = DatabaseAccess.of(ctx)
        val tables = db.getBlockchainTables(ctx).map { it.substringAfter(".") }.toSet()
        return tables == setOf("configurations", "blocks", "transactions")
    }
}
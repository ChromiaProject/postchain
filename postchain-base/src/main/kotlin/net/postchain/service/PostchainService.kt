package net.postchain.service

import net.postchain.PostchainNode
import net.postchain.api.internal.PostchainApi
import net.postchain.base.BlockchainRelatedInfo
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.core.BadDataMistake
import net.postchain.gtv.Gtv

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
                PostchainApi.addConfiguration(ctx, height, override, config, allowUnknownSigners)
            }

    /**
     * @return [BlockchainRid] if chain was initialized, `null` if already existed and `override` is `false`
     */
    fun initializeBlockchain(chainId: Long, maybeBrid: BlockchainRid?, override: Boolean, config: Gtv,
                             givenDependencies: List<BlockchainRelatedInfo> = listOf()): BlockchainRid? {
        val brid = maybeBrid
                ?: GtvToBlockchainRidFactory.calculateBlockchainRid(config, postchainNode.postchainContext.cryptoSystem)

        val initialized = withWriteConnection(postchainNode.postchainContext.storage, chainId) { ctx ->
            PostchainApi.initializeBlockchain(ctx, brid, override, config, givenDependencies)
        }

        return if (initialized) brid else null
    }

    fun findBlockchain(chainId: Long): Pair<BlockchainRid, Boolean>? =
            withReadConnection(postchainNode.postchainContext.storage, chainId) { ctx ->
                PostchainApi.findBlockchain(ctx)
            }?.let { Pair(it, postchainNode.isBlockchainRunning(chainId)) }

    fun addBlockchainReplica(brid: String, pubkey: String): Boolean =
            postchainNode.postchainContext.storage.withWriteConnection { ctx ->
                PostchainApi.addBlockchainReplica(ctx, brid, pubkey)
            }

    fun removeBlockchainReplica(brid: String, pubkey: String): Set<BlockchainRid> =
            postchainNode.postchainContext.storage.withWriteConnection { ctx ->
                PostchainApi.removeBlockchainReplica(ctx, brid, pubkey)
            }
}

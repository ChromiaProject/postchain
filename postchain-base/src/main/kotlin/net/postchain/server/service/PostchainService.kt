package net.postchain.server.service

import net.postchain.PostchainNode
import net.postchain.api.internal.BlockchainApi
import net.postchain.base.BlockchainRelatedInfo
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.core.BadDataMistake
import net.postchain.crypto.PubKey
import net.postchain.debug.DiagnosticData
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.DiagnosticQueue
import net.postchain.debug.EagerDiagnosticValue
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
                BlockchainApi.addConfiguration(ctx, height, override, config, allowUnknownSigners)
            }

    /**
     * @return [BlockchainRid] if chain was initialized, `null` if already existed and `override` is `false`
     */
    fun initializeBlockchain(chainId: Long, maybeBrid: BlockchainRid?, override: Boolean, config: Gtv,
                             givenDependencies: List<BlockchainRelatedInfo> = listOf()): BlockchainRid? {
        val brid = maybeBrid
                ?: GtvToBlockchainRidFactory.calculateBlockchainRid(config, postchainNode.postchainContext.cryptoSystem)

        try {
            val initialized = withWriteConnection(postchainNode.postchainContext.storage, chainId) { ctx ->
                BlockchainApi.initializeBlockchain(ctx, brid, override, config, givenDependencies)
            }
            return if (initialized) brid else null
        } catch (e: Exception) {
            val bcData = postchainNode.postchainContext.nodeDiagnosticContext.blockchainDiagnosticData.getOrPut(brid) {
                DiagnosticData(DiagnosticProperty.BLOCKCHAIN_RID to EagerDiagnosticValue(brid.toHex()),
                        DiagnosticProperty.ERROR to DiagnosticQueue<String>(5))
            }
            val errors = bcData[DiagnosticProperty.ERROR] as DiagnosticQueue<String>
            errors.add(e.message)
            return null
        }

    }

    fun findBlockchain(chainId: Long): Triple<BlockchainRid?, Boolean?, Long> =
            withReadConnection(postchainNode.postchainContext.storage, chainId) { ctx ->
                Triple(
                        BlockchainApi.findBlockchain(ctx),
                        postchainNode.isBlockchainRunning(chainId),
                        BlockchainApi.getLastBlockHeight(ctx)
                )
            }

    fun addBlockchainReplica(brid: BlockchainRid, pubkey: PubKey): Boolean =
            postchainNode.postchainContext.storage.withWriteConnection { ctx ->
                BlockchainApi.addBlockchainReplica(ctx, brid, pubkey)
            }

    fun removeBlockchainReplica(brid: BlockchainRid, pubkey: PubKey): Set<BlockchainRid> =
            postchainNode.postchainContext.storage.withWriteConnection { ctx ->
                BlockchainApi.removeBlockchainReplica(ctx, brid, pubkey)
            }
}

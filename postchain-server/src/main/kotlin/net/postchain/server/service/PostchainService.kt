package net.postchain.server.service

import net.postchain.PostchainNode
import net.postchain.api.internal.BlockchainApi
import net.postchain.base.BlockchainRelatedInfo
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.importexport.ExportResult
import net.postchain.base.importexport.ImportResult
import net.postchain.base.importexport.ImporterExporter
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.core.BadDataMistake
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PrivKey
import net.postchain.crypto.PubKey
import net.postchain.gtv.Gtv
import net.postchain.server.NodeProvider
import java.nio.file.Path

class PostchainService(private val nodeProvider: NodeProvider) {

    private val postchainNode: PostchainNode get() = nodeProvider.get()

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

        return try {
            val initialized = withWriteConnection(postchainNode.postchainContext.storage, chainId) { ctx ->
                BlockchainApi.initializeBlockchain(ctx, brid, override, config, givenDependencies)
            }
            if (initialized) brid else null
        } catch (e: Exception) {
            postchainNode.postchainContext.nodeDiagnosticContext.blockchainErrorQueue(brid).add(e.message)
            throw InitializationError(e.message)
        }

    }

    class InitializationError(m: String?) : RuntimeException(m)

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

    fun exportBlockchain(chainId: Long, configurationFile: Path, blocksFile: Path, overwrite: Boolean, fromHeight: Long, upToHeight: Long): ExportResult =
            ImporterExporter.exportBlockchain(
                    postchainNode.postchainContext.storage,
                    chainId,
                    configurationsFile = configurationFile,
                    blocksFile = blocksFile,
                    overwrite = overwrite,
                    fromHeight = fromHeight,
                    upToHeight = upToHeight)

    fun importBlockchain(chainId: Long, configurationFile: Path, blocksFile: Path, incremental: Boolean): ImportResult =
            ImporterExporter.importBlockchain(
                    KeyPair(PubKey(postchainNode.appConfig.pubKeyByteArray), PrivKey(postchainNode.appConfig.privKeyByteArray)),
                    postchainNode.postchainContext.cryptoSystem,
                    postchainNode.postchainContext.storage,
                    chainId,
                    configurationsFile = configurationFile,
                    blocksFile = blocksFile,
                    incremental)
}
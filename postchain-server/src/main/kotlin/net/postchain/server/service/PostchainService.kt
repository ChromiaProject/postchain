package net.postchain.server.service

import net.postchain.PostchainNode
import net.postchain.api.internal.BlockchainApi
import net.postchain.base.BlockchainRelatedInfo
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.importexport.ExportResult
import net.postchain.base.importexport.ImportResult
import net.postchain.base.importexport.ImporterExporter
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.NotFound
import net.postchain.core.BadDataException
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PrivKey
import net.postchain.crypto.PubKey
import net.postchain.debug.ErrorDiagnosticValue
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
     * @throws BadDataException if signer pubkey does not exist in peerinfos and allowUnknownSigners is false
     */
    fun addConfiguration(chainId: Long, height: Long, override: Boolean, config: Gtv, allowUnknownSigners: Boolean = false): Boolean =
            withWriteConnection(postchainNode.postchainContext.sharedStorage, chainId) { ctx ->
                BlockchainApi.addConfiguration(ctx, height, override, config, allowUnknownSigners)
            }

    fun listConfigurations(chainId: Long): List<Long> {
        return withReadConnection(postchainNode.postchainContext.sharedStorage, chainId) { ctx ->
            BlockchainApi.listConfigurations(ctx)
        }
    }

    /**
     * @return [BlockchainRid] if chain was initialized, `null` if already existed and `override` is `false`
     */
    fun initializeBlockchain(chainId: Long, maybeBrid: BlockchainRid?, override: Boolean, config: Gtv,
                             givenDependencies: List<BlockchainRelatedInfo> = listOf()): BlockchainRid? {
        val brid = maybeBrid
                ?: GtvToBlockchainRidFactory.calculateBlockchainRid(config, postchainNode.postchainContext.cryptoSystem)

        return try {
            val initialized = withWriteConnection(postchainNode.postchainContext.sharedStorage, chainId) { ctx ->
                BlockchainApi.initializeBlockchain(ctx, brid, override, config, givenDependencies)
            }
            if (initialized) brid else null
        } catch (e: Exception) {
            postchainNode.postchainContext.nodeDiagnosticContext.blockchainErrorQueue(brid).add(
                    ErrorDiagnosticValue(
                            e.message ?: "Failed to initialize blockchain with chainId: $chainId",
                            System.currentTimeMillis()
                    )
            )
            throw InitializationError(e.message)
        }

    }

    class InitializationError(m: String?) : RuntimeException(m)

    fun findBlockchain(chainId: Long): Triple<BlockchainRid?, Boolean?, Long> =
            withReadConnection(postchainNode.postchainContext.sharedStorage, chainId) { ctx ->
                BlockchainApi.findBlockchain(ctx)?.let {
                    Triple(
                            it,
                            postchainNode.isBlockchainRunning(chainId),
                            BlockchainApi.getLastBlockHeight(ctx)
                    )
                } ?: Triple(null, null, -1)
            }

    fun addBlockchainReplica(brid: BlockchainRid, pubkey: PubKey): Boolean =
            postchainNode.postchainContext.sharedStorage.withWriteConnection { ctx ->
                BlockchainApi.addBlockchainReplica(ctx, brid, pubkey)
            }

    fun removeBlockchainReplica(brid: BlockchainRid, pubkey: PubKey): Set<BlockchainRid> =
            postchainNode.postchainContext.sharedStorage.withWriteConnection { ctx ->
                BlockchainApi.removeBlockchainReplica(ctx, brid, pubkey)
            }

    fun exportBlockchain(chainId: Long, configurationFile: Path, blocksFile: Path?, overwrite: Boolean, fromHeight: Long, upToHeight: Long): ExportResult =
            ImporterExporter.exportBlockchain(
                    postchainNode.postchainContext.sharedStorage,
                    chainId,
                    configurationsFile = configurationFile,
                    blocksFile = blocksFile,
                    overwrite = overwrite,
                    fromHeight = fromHeight,
                    upToHeight = upToHeight)

    fun importBlockchain(chainId: Long, blockchainRidData: ByteArray, configurationFile: Path, blocksFile: Path, incremental: Boolean): ImportResult {
        val chainId0 = if (blockchainRidData.isNotEmpty()) {
            val brid = BlockchainRid(blockchainRidData)
            postchainNode.postchainContext.sharedStorage.withReadConnection {
                DatabaseAccess.of(it).getChainId(it, brid)
                        ?: throw NotFound("Blockchain not found by RID: $brid")
            }
        } else {
            chainId
        }

        return ImporterExporter.importBlockchain(
                KeyPair(PubKey(postchainNode.appConfig.pubKeyByteArray), PrivKey(postchainNode.appConfig.privKeyByteArray)),
                postchainNode.postchainContext.cryptoSystem,
                postchainNode.postchainContext.sharedStorage,
                chainId0,
                configurationsFile = configurationFile,
                blocksFile = blocksFile,
                incremental)
    }

    fun removeBlockchain(chainId: Long) {
        stopBlockchain(chainId)

        withWriteConnection(postchainNode.postchainContext.sharedStorage, chainId) { ctx ->
            BlockchainApi.deleteBlockchain(ctx)
            true
        }
    }
}

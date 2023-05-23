// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.base.BaseBlockWitness
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.containers.bpm.ContainerBlockchainProcessManagerExtension
import net.postchain.core.BlockRid
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainProcess
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtx.GtxBuilder
import java.util.*

/**
 * TODO: Olle: this is currently used, via configuration. It will be replaced by the new Anchoring process.
 */
class LegacyAnchoringBlockchainProcessManagerExtension(private val postchainContext: PostchainContext) : ContainerBlockchainProcessManagerExtension {
    private var chain0BlockchainEngine: BlockchainEngine? = null

    companion object : KLogging()

    override fun afterCommitInSubnode(blockchainRid: BlockchainRid, blockRid: BlockRid, blockHeader: ByteArray, witnessData: ByteArray) {
        val chain0Engine = chain0BlockchainEngine
        if (chain0Engine != null) {
            insertAnchorOperation(chain0Engine, blockHeader, witnessData)
        } else {
            logger.warn("Could not anchor block from subnode for chain: ${blockchainRid.toHex()}")
        }
    }

    override fun afterCommit(process: BlockchainProcess, height: Long) {
        val chainId = process.blockchainEngine.getConfiguration().chainID
        if (chainId != 0L && process.isSigner()) {
            val chain0Engine = chain0BlockchainEngine
            if (chain0Engine != null) {
                try {
                    anchorLastBlock(chainId, chain0Engine)
                } catch (e: Exception) {
                    logger.error(e) { "Error when anchoring: $e" }
                }
            } else {
                logger.warn("Could not anchor block for chainId: $chainId at height: $height")
            }
        }
    }

    override fun connectProcess(process: BlockchainProcess) {
        if (process.blockchainEngine.getConfiguration().chainID == 0L) {
            chain0BlockchainEngine = process.blockchainEngine
        }
    }

    override fun disconnectProcess(process: BlockchainProcess) {
        if (process.blockchainEngine.getConfiguration().chainID == 0L) {
            chain0BlockchainEngine = null
        }
    }

    override fun shutdown() {}

    private fun anchorLastBlock(chainId: Long, chain0Engine: BlockchainEngine) {
        withReadConnection(postchainContext.storage, chainId) { eContext ->
            val db = DatabaseAccess.of(eContext)
            val blockRID = db.getLastBlockRid(eContext, chainId)
            if (blockRID != null) {
                val blockHeader = db.getBlockHeader(eContext, blockRID)
                val witnessData = db.getWitnessData(eContext, blockRID)
                insertAnchorOperation(chain0Engine, blockHeader, witnessData)
            }
        }
    }

    private fun insertAnchorOperation(chain0Engine: BlockchainEngine, blockHeader: ByteArray, witnessData: ByteArray) {
        val witness = BaseBlockWitness.fromBytes(witnessData)
        val txb = GtxBuilder(chain0Engine.getConfiguration().blockchainRid, listOf(), postchainContext.cryptoSystem)
        // sorting signatures makes it more likely we can avoid duplicate anchor transactions
        val sortedSignatures = witness.getSignatures().sortedWith { o1, o2 -> Arrays.compareUnsigned(o1.subjectID, o2.subjectID) }
        txb.addOperation(
                "anchor_block",
                GtvDecoder.decodeGtv(blockHeader),
                GtvArray(sortedSignatures.map { GtvByteArray(it.subjectID) }.toTypedArray()),
                GtvArray(sortedSignatures.map { GtvByteArray(it.data) }.toTypedArray())
        )
        val tx = chain0Engine.getConfiguration().getTransactionFactory().decodeTransaction(
                txb.finish().buildGtx().encode()
        )
        chain0Engine.getTransactionQueue().enqueue(tx)
    }
}

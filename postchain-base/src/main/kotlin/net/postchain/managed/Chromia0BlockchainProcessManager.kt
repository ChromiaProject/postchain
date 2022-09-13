// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockWitness
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.data.ByteArrayKey
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.AfterCommitHandler
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.block.BlockTrace
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtx.GtxBuilder

/**
 * TODO: Olle: this is currently used, via configuration. It will be replaced by the new Anchoring process.
 */
class Chromia0BlockchainProcessManager(
    postchainContext: PostchainContext,
    blockchainInfrastructure: BlockchainInfrastructure,
    blockchainConfigProvider: BlockchainConfigurationProvider
) : ManagedBlockchainProcessManager(
    postchainContext,
    blockchainInfrastructure,
    blockchainConfigProvider
) {

    private fun anchorLastBlock(chainId: Long) {
        withReadConnection(storage, chainId) { eContext ->
            val db = DatabaseAccess.of(eContext)
            val blockRID = db.getLastBlockRid(eContext, chainId)
            if (blockRID != null) {
                val chain0Engine = blockchainProcesses[0L]!!.blockchainEngine
                val blockHeader = db.getBlockHeader(eContext, blockRID)
                val witnessData = db.getWitnessData(eContext, blockRID)
                val witness = BaseBlockWitness.fromBytes(witnessData)
                val txb = GtxBuilder(chain0Engine.getConfiguration().blockchainRid, listOf(), Secp256K1CryptoSystem())
                // sorting signatures makes it more likely we can avoid duplicate anchor transactions
                val sortedSignatures = witness.getSignatures().sortedBy { ByteArrayKey(it.subjectID) }
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
    }

    override fun buildAfterCommitHandler(chainId: Long): AfterCommitHandler {
        val baseHandler = super.buildAfterCommitHandler(chainId)
        if (chainId == 0L)
            return baseHandler
        else {
            return { bTrace: BlockTrace?, height: Long, blockTimestamp: Long ->
                rhTrace("Begin", chainId, bTrace)
                try {
                    anchorLastBlock(chainId)
                    rhTrace("Anchored", chainId, bTrace)
                } catch (e: Exception) {
                    logger.error("Error when anchoring $e", e)
                    e.printStackTrace()
                }
                baseHandler(bTrace, height, blockTimestamp)
            }
        }
    }

    //  restartHandler()
    private fun rhTrace(str: String, chainId: Long, bTrace: BlockTrace?) {
        if (logger.isTraceEnabled) {
            logger.trace("[${nodeName()}]: RestartHandler CHROMIA 0 -- $str: chainId: $chainId, block causing handler to run: $bTrace")
        }
    }
}
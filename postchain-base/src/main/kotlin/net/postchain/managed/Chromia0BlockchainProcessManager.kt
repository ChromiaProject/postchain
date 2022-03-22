// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockWitness
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.ByteArrayKey
import net.postchain.core.AfterCommitHandler
import net.postchain.debug.BlockTrace
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtx.GTXDataBuilder

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
        blockchainConfigProvider) {

    private fun anchorLastBlock(chainId: Long) {
        withReadConnection(storage, chainId) { eContext ->
            val dba = DatabaseAccess.of(eContext)
            val blockRID = dba.getLastBlockRid(eContext, chainId)
            val chain0Engine = blockchainProcesses[0L]!!.blockchainEngine
            if (blockRID != null) {
                val blockHeader = dba.getBlockHeader(eContext, blockRID)
                val witnessData = dba.getWitnessData(eContext, blockRID)
                val witness = BaseBlockWitness.fromBytes(witnessData)
                val txb = GTXDataBuilder(chain0Engine.getConfiguration().blockchainRid,
                        arrayOf(), SECP256K1CryptoSystem())
                // sorting signatures makes it more likely we can avoid duplicate anchor transactions
                val sortedSignatures = witness.getSignatures().sortedBy { ByteArrayKey(it.subjectID) }
                txb.addOperation("anchor_block",
                        arrayOf(
                                GtvDecoder.decodeGtv(blockHeader),
                                GtvArray(
                                        sortedSignatures.map { GtvByteArray(it.subjectID) }.toTypedArray()
                                ),
                                GtvArray(
                                        sortedSignatures.map { GtvByteArray(it.data) }.toTypedArray()
                                )
                        )
                )
                txb.finish()
                val tx = chain0Engine.getConfiguration().getTransactionFactory().decodeTransaction(
                        txb.serialize()
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
            return { bTrace: BlockTrace?, height: Long ->
                rhTrace("Begin", chainId, bTrace)
                try {
                    anchorLastBlock(chainId)
                    rhTrace("Anchored", chainId, bTrace)
                } catch (e: Exception) {
                    logger.error("Error when anchoring $e", e)
                    e.printStackTrace()
                }
                baseHandler(bTrace, height)
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
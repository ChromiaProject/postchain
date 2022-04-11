package net.postchain.ebft.syncmanager.validator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import mu.KLogging
import net.postchain.common.toHex
import net.postchain.core.NodeRid
import net.postchain.core.ProgrammerMistake
import net.postchain.core.Shutdownable
import net.postchain.ebft.BlockDatabase
import net.postchain.ebft.BlockManager
import net.postchain.ebft.StatusManager
import net.postchain.ebft.message.*
import net.postchain.ebft.worker.WorkerContext

class BlockAndSignatureSender(
        private val blockDatabase: BlockDatabase,
        private val blockManager: BlockManager,
        private val statusManager: StatusManager,
        workerContext: WorkerContext
) : Shutdownable {
    private val communicationManager = workerContext.communicationManager
    private val signersIds = workerContext.blockchainConfiguration.signers.map { NodeRid(it) }

    companion object : KLogging()

    private var blockMessagesJob: Job

    init {
        blockMessagesJob = CoroutineScope(Dispatchers.Default).launch {
            communicationManager.messages
                    .collect {
                        val (nodeId, message) = it
                        when (message) {
                            is GetUnfinishedBlock -> sendUnfinishedBlock(nodeId)
                            is GetBlockSignature -> sendBlockSignature(nodeId, message.blockRID)
                        }
                    }
        }
    }

    override fun shutdown() {
        blockMessagesJob.cancel()
    }

    private fun validatorAtIndex(index: Int): NodeRid = signersIds[index]
    private fun indexOfValidator(peerId: NodeRid): Int = signersIds.indexOf(peerId)

    /**
     * Send message to peer with our commit signature
     *
     * @param nodeId node id of receiving peer
     * @param blockRID block identifier
     */
    private fun sendBlockSignature(nodeId: NodeRid, blockRID: ByteArray) {
        val nodeIndex = indexOfValidator(nodeId)
        val currentBlock = blockManager.currentBlock
        if (currentBlock != null && currentBlock.header.blockRID.contentEquals(blockRID)) {
            if (!statusManager.myStatus.blockRID!!.contentEquals(currentBlock.header.blockRID)) {
                throw ProgrammerMistake("status manager block RID (${statusManager.myStatus.blockRID!!.toHex()}) out of sync with current block RID (${currentBlock.header.blockRID.toHex()})")
            }
            val signature = statusManager.getCommitSignature()
            if (signature != null) {
                communicationManager.sendPacket(BlockSignature(
                        blockRID,
                        Signature(signature.subjectID, signature.data)),
                        validatorAtIndex(nodeIndex))
            }
            return
        }
        val blockSignature = blockDatabase.getBlockSignature(blockRID)
        blockSignature success {
            val packet = BlockSignature(blockRID, Signature(it.subjectID, it.data))
            communicationManager.sendPacket(packet, validatorAtIndex(nodeIndex))
        } fail {
            logger.error("Error sending BlockSignature")
        }
    }

    /**
     * Send message to node with the current unfinished block.
     *
     * @param nodeId id of node to send block to
     */
    private fun sendUnfinishedBlock(nodeId: NodeRid) {
        val nodeIndex = indexOfValidator(nodeId)
        val currentBlock = blockManager.currentBlock
        if (currentBlock != null) {
            communicationManager.sendPacket(UnfinishedBlock(currentBlock.header.rawData, currentBlock.transactions.toList()),
                    validatorAtIndex(nodeIndex))
        }
    }
}
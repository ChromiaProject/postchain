// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft

import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.core.block.BlockTrace
import net.postchain.crypto.Signature
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

interface ErrContext {
    fun fatal(msg: String)
    fun warn(msg: String)
    fun log(msg: String)
}

enum class NodeBlockState {
    WaitBlock, // PBFT: before PRE-PREPARE
    HaveBlock, // PBFT: after PRE-PREPARE, PREPARE message is sent
    Prepared   // PBFT: _prepared_ state, COMMIT message is sent
}

/**
 * @param height The hight of the next block to be built. Ie current committed
 * height + 1
 */
class NodeStatus(var height: Long, var serial: Long) {

    var state: NodeBlockState = NodeBlockState.WaitBlock
    var round: Long = 0  // PBFT: view-number
    var blockRID: ByteArray? = null
    var configHash: ByteArray? = null

    var revolting: Boolean = false // PBFT: VIEW-CHANGE (?)

    constructor () : this(0, -1)
}

interface BlockDatabase {
    fun getQueuedBlockCount(): Int
    fun addBlock(block: BlockDataWithWitness, dependsOn: CompletableFuture<Unit>?, existingBTrace: BlockTrace?): CompletableFuture<Unit> // add a complete block after the current one
    fun loadUnfinishedBlock(block: BlockData): CompletionStage<Signature> // returns block signature if successful
    fun commitBlock(signatures: Array<Signature?>): CompletionStage<Unit>
    fun buildBlock(): CompletionStage<Pair<BlockData, Signature>>

    fun verifyBlockSignature(s: Signature): Boolean
    fun getBlockSignature(blockRID: ByteArray): CompletionStage<Signature>
    fun getBlockAtHeight(height: Long, includeTransactions: Boolean = true): CompletionStage<BlockDataWithWitness?>

    fun setBlockTrace(blockTrace: BlockTrace) // Only debugging
}

sealed class BlockIntent {
    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (super.equals(other)) return true
        if (this::class != other::class) return false
        return true
    }

    override fun hashCode(): Int {
        return 42
    }
}

object DoNothingIntent : BlockIntent() {
    override fun toString(): String {
        return "DNI"
    }
}

object CommitBlockIntent : BlockIntent() {
    override fun toString(): String {
        return "CBI"
    }
}

object BuildBlockIntent : BlockIntent() {
    override fun toString(): String {
        return "BBI"
    }
}

class FetchBlockAtHeightIntent(val height: Long) : BlockIntent() {
    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) return false
        if (other !is FetchBlockAtHeightIntent) return false
        return height == other.height
    }

    override fun hashCode(): Int {
        return height.hashCode()
    }
}

class FetchUnfinishedBlockIntent(val blockRID: ByteArray) : BlockIntent() {

    fun isThisTheBlockWeAreWaitingFor(givenBlockRID: ByteArray?): Boolean {
        if (givenBlockRID == null) {
            return false
        } else {
            return Arrays.equals(blockRID, givenBlockRID)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) return false
        if (this === other) return true
        other as FetchUnfinishedBlockIntent
        if (!blockRID.contentEquals(other.blockRID)) return false
        return true
    }

    override fun hashCode(): Int {
        return Arrays.hashCode(blockRID)
    }
}


data class FetchCommitSignatureIntent(val blockRID: ByteArray, val nodes: Array<Int>) : BlockIntent() {
    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) return false
        if (this === other) return true
        other as FetchCommitSignatureIntent
        if (!blockRID.contentEquals(other.blockRID)) return false
        if (!Arrays.equals(nodes, other.nodes)) return false
        return true
    }

    override fun hashCode(): Int {
        return Arrays.hashCode(blockRID) + Arrays.hashCode(nodes)
    }
}

interface BlockManager {
    var currentBlock: BlockData?
    var lastBlockTimestamp: Long?
    fun onReceivedUnfinishedBlock(block: BlockData)
    fun onReceivedBlockAtHeight(block: BlockDataWithWitness, height: Long)
    fun processBlockIntent(): BlockIntent
    fun getBlockIntent(): BlockIntent
}

/**
 * Manages the status of the consensus protocol
 */
interface StatusManager {
    val nodeStatuses: Array<NodeStatus>
    val commitSignatures: Array<Signature?>
    val myStatus: NodeStatus

    fun getMyIndex(): Int
    fun isMyNodePrimary(): Boolean
    fun primaryIndex(): Int
    fun onStatusUpdate(nodeIndex: Int, status: NodeStatus) // STATUS message from another node
    fun fastForwardHeight(committedHeight: Long): Boolean
    fun onHeightAdvance(height: Long): Boolean // a complete block was received from other peers, go forward
    fun onCommittedBlock(blockRID: ByteArray) // when block committed to the database
    fun onReceivedBlock(blockRID: ByteArray, mySignature: Signature): Boolean // received block was validated by BlockManager/DB
    fun onBuiltBlock(blockRID: ByteArray, mySignature: Signature): Boolean // block built by BlockManager/BlockDatabase (on a primary node)
    fun onCommitSignature(nodeIndex: Int, blockRID: ByteArray, signature: Signature)
    fun onStartRevolting()

    fun getBlockIntent(): BlockIntent
    fun getCommitSignature(): Signature?
    fun getLatestStatusTimestamp(nodeIndex: Int): Long
}

class BDBAbortException(val block: BlockDataWithWitness, val prev: CompletableFuture<Unit>) :
        RuntimeException("BlockDatabase aborted execution of an addBlock task because previous task failed")

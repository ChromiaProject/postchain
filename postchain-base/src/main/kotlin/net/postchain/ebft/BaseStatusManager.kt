// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft

import io.micrometer.core.instrument.Counter
import mu.KLogging
import mu.withLoggingContext
import net.postchain.common.toHex
import net.postchain.core.NodeRid
import net.postchain.crypto.Signature
import net.postchain.getBFTRequiredSignatureCount
import net.postchain.logging.REVOLTED_ON_NODE_TAG
import net.postchain.logging.REVOLTING_NODE_TAG
import net.postchain.metrics.NodeStatusMetrics
import java.time.Clock
import java.util.Arrays

/**
 * StatusManager manages the status of the consensus protocol
 */
class BaseStatusManager(
        nodes: List<ByteArray>,
        private val myIndex: Int,
        myNextHeight: Long,
        private val nodeStatusMetrics: NodeStatusMetrics,
        private val clock: Clock
) : StatusManager {
    private val nodeCount = nodes.size
    override val nodeStatuses = Array(nodeCount) { NodeStatus() }
    override val commitSignatures: Array<Signature?> = arrayOfNulls(nodeCount)
    override val myStatus: NodeStatus = nodeStatuses[myIndex]
    var intent: BlockIntent = DoNothingIntent
    private val quorum = getBFTRequiredSignatureCount(nodeCount)
    private val nodeStatusesTimestamps = Array(nodeCount) { 0L }
    val nodeReportedRevolt = Array(nodeCount) { false }
    private val nodeRids = nodes.map { NodeRid(it) }

    companion object : KLogging() {
        const val ZERO_SERIAL_TIME = 1518000000000L
    }

    init {
        myStatus.height = myNextHeight
        // make sure that after restart status updates are still considered fresh,
        // this works fine as long as we have fewer than 1000 updates per second,
        // otherwise we are screwed
        myStatus.serial = currentTimeMillis() - ZERO_SERIAL_TIME
    }

    private fun currentTimeMillis() = clock.millis()

    override fun getMyIndex() = myIndex

    /**
     * Count the number of nodes that are at [height] with the tip being [blockRID]
     *
     * @param state the local state
     * @param height block height
     * @param blockRID latest block
     * @return the number of nodes at the same state as the local node
     */
    private fun countNodes(state: NodeBlockState, height: Long, blockRID: ByteArray?): Int {
        var count = 0
        for (ns in nodeStatuses) {
            if (ns.height == height && ns.state == state) {
                if (blockRID == null) {
                    if (ns.blockRID == null) count++
                } else {
                    if (ns.blockRID != null && ns.blockRID.contentEquals(blockRID))
                        count++
                }
            }
        }
        return count
    }


    /**
     * Update status of peer node
     *
     * @param nodeIndex index of node to be updated
     * @param status new status
     */
    @Synchronized
    override fun onStatusUpdate(nodeIndex: Int, status: NodeStatus) {
        nodeStatusesTimestamps[nodeIndex] = currentTimeMillis()
        val existingStatus = nodeStatuses[nodeIndex]
        reportRevolt(nodeIndex, status)
        if (
        // A restarted peer will have its serial reset, but
        // this will not cause a problem because the serial is
        // initialized based on current time. See init block
        // of this class
                (status.serial > existingStatus.serial)
                || (status.height > existingStatus.height)
                || ((status.height == existingStatus.height) && (status.round > existingStatus.round))
        ) {
            nodeStatuses[nodeIndex] = status
            recomputeStatus()
        }
    }

    /**
     * Log and increase metrics if node is revolting
     */
    fun reportRevolt(nodeIndex: Int, status: NodeStatus) {
        if (status.revolting) {
            // The height check is to ensure that we have the same configuration as the revolting node
            // so that we have the same signers list when we look up the revolted node
            if (!nodeReportedRevolt[nodeIndex] && status.height == myStatus.height) {
                logRevolt(nodeIndex, status)
                nodeReportedRevolt[nodeIndex] = true
            }
        } else nodeReportedRevolt[nodeIndex] = false
    }

    fun logRevolt(revoltingNodeIndex: Int, status: NodeStatus) {
        val revoltingNode = nodeRids[revoltingNodeIndex]
        val revoltedOnNodeIndex = calculateIndex(status.height, status.round)
        val revoltedOnNode = nodeRids[revoltedOnNodeIndex]
        withLoggingContext(REVOLTED_ON_NODE_TAG to revoltedOnNode.toString(), REVOLTING_NODE_TAG to revoltingNode.toString()) {
            logger.info("Node $revoltingNode has revolted against $revoltedOnNode")
        }
        getRevoltCounter(revoltingNodeIndex, revoltedOnNodeIndex).increment()
    }

    private fun getRevoltCounter(nodeIndex: Int, revoltedOnNodeIndex: Int): Counter =
            when (myIndex) {
                nodeIndex -> nodeStatusMetrics.revoltsByNode
                revoltedOnNodeIndex -> nodeStatusMetrics.revoltsOnNode
                else -> nodeStatusMetrics.revoltsBetweenOthers
            }

    /**
     * Advance height in [myStatus] as a response to committing a new block.
     */
    private fun advanceHeight() {
        with(myStatus) {
            height += 1
            serial += 1
            blockRID = null
            round = 0
            revolting = false
            state = NodeBlockState.WaitBlock
        }
        resetCommitSignatures()
        intent = DoNothingIntent
        recomputeStatus()
    }

    /**
     * Set all commit signatures to null
     */
    private fun resetCommitSignatures() {
        for (i in commitSignatures.indices)
            commitSignatures[i] = null
    }

    /**
     * Advance height in local status if it has incremented with one
     *
     * @param height the new height
     * @return success or failure
     */
    @Synchronized
    override fun onHeightAdvance(height: Long): Boolean =
            if (height == (myStatus.height + 1)) {
                advanceHeight()
                true
            } else {
                logger.error("Height mismatch my height: ${myStatus.height} new height: $height")
                false
            }

    /**
     * Fast forward height
     *
     * @param committedHeight the new committed height
     * @return success or failure
     */
    @Synchronized
    override fun fastForwardHeight(committedHeight: Long): Boolean {
        val nextHeight = committedHeight + 1
        if (nextHeight < myStatus.height) {
            logger.error("Failed to fast forward negative increment.")
            return false
        }

        logger.debug { "Advancing block height from ${myStatus.height} to $nextHeight ..." }
        (myStatus.height until nextHeight).forEach { _ -> advanceHeight() }

        logger.debug { "Current state: ${myStatus.height}" }
        return true
    }

    /**
     * Block is committed
     *
     * @param blockRID the committed block
     */
    @Synchronized
    override fun onCommittedBlock(blockRID: ByteArray) {
        if (blockRID.contentEquals(myStatus.blockRID)) {
            advanceHeight()
        } else
            logger.error("Committed block with wrong RID")
    }

    /**
     * Accept block as valid
     *
     * @param blockRID block identifier
     * @param mySignature signature of [blockRID]
     */
    fun acceptBlock(blockRID: ByteArray, mySignature: Signature) {
        resetCommitSignatures()
        myStatus.blockRID = blockRID
        myStatus.serial += 1
        myStatus.state = NodeBlockState.HaveBlock
        commitSignatures[myIndex] = mySignature
        intent = DoNothingIntent
        recomputeStatus()
    }

    /**
     * When block is received from peer
     *
     * @param blockRID received block
     * @param mySignature signature for block
     * @return success or failure
     */
    @Synchronized
    override fun onReceivedBlock(blockRID: ByteArray, mySignature: Signature): Boolean {
        val theIntent = intent
        return if (theIntent is FetchUnfinishedBlockIntent) {
            if (theIntent.isThisTheBlockWeAreWaitingFor(blockRID)) {
                acceptBlock(blockRID, mySignature)
                true
            } else {
                logger.info("Received block which is irrelevant. Need ${theIntent.blockRID.toHex()}, got ${blockRID.toHex()}")
                false
            }
        } else {
            logger.info("Received block which is irrelevant, intent was ${theIntent::class.simpleName}")
            false
        }
    }


    /**
     * Return the index of the primary node, ie the node tasked with creating the next block.
     *
     * @return primary node index
     */
    override fun primaryIndex(): Int = calculateIndex(myStatus.height, myStatus.round)

    private fun calculateIndex(height: Long, round: Long): Int = ((height + round) % nodeCount).toInt()

    override fun isMyNodePrimary(): Boolean {
        return primaryIndex() == this.myIndex
    }

    /**
     * Run when new block has been built locally
     *
     * @param blockRID identifier of newly built block
     * @param mySignature signature of newly built block
     * @return success or failure
     */
    @Synchronized
    override fun onBuiltBlock(blockRID: ByteArray, mySignature: Signature): Boolean {
        if (intent is BuildBlockIntent) {
            if (!isMyNodePrimary()) {
                logger.warn("Inconsistent state: building a block while not a primary: blockRid = ${blockRID.toHex()}")
                intent = DoNothingIntent
                return false
            }
            acceptBlock(blockRID, mySignature)
            return true
        } else {
            logger.warn("Received built block while not requesting it: blockRid = ${blockRID.toHex()}")
            return false
        }
    }

    /**
     * Run when signature from [nodeIndex] is committed. Will result in re-computation of local status
     *
     * @param nodeIndex index of node signature is from
     * @param blockRID identifier of block signature applies to
     * @param signature the signature of [blockRID]
     */
    @Synchronized
    override fun onCommitSignature(nodeIndex: Int, blockRID: ByteArray, signature: Signature) {
        if (myStatus.state == NodeBlockState.Prepared
                && Arrays.equals(blockRID, myStatus.blockRID)) {
            this.commitSignatures[nodeIndex] = signature
            recomputeStatus()
        } else {
            logger.warn("Wrong commit signature")
        }
    }

    /**
     * A revolt has started. Revolts occur when the primary node fails to create a new block
     */
    @Synchronized
    override fun onStartRevolting() {
        myStatus.revolting = true
        myStatus.serial += 1
        logRevolt(myIndex, myStatus)
        recomputeStatus()
    }

    /**
     * Get intent
     *
     * @return the intent
     */
    @Synchronized
    override fun getBlockIntent(): BlockIntent = intent

    fun setBlockIntent(newIntent: BlockIntent) {
        intent = newIntent
    }

    /**
     * Get local commit signature
     *
     * @return the signature
     */
    override fun getCommitSignature(): Signature? {
        return this.commitSignatures[myIndex]
    }

    /**
     * Retrieve the time of the latest received status update for a node
     */
    override fun getLatestStatusTimestamp(nodeIndex: Int): Long {
        return nodeStatusesTimestamps[nodeIndex]
    }

    /**
     * Recompute status until no more updates occur.
     */
    fun recomputeStatus() {
        for (i in 0..1000) {
            if (!shouldRecomputeStatusAgain()) break
        }
    }


    // Simple helper class to keep track of when our work is done
    enum class FlowStatus {
        JustRunOn, Continue, Break
    }

    /**
     * Recompute our status by updating what state we are in (WaitBlock, HaveBlock or Prepared). Also update
     * our intents, ie what we need to do next.
     *
     * @return true if status is updated
     */
    fun shouldRecomputeStatusAgain(): Boolean { // Not private bc unit test

        /**
         * Used to reset our block status when we are in HaveBlock state
         */
        fun resetBlock() {
            myStatus.state = NodeBlockState.WaitBlock
            myStatus.blockRID = null
            myStatus.serial += 1
            resetCommitSignatures()
        }

        /**
         * If we find that the node status are ahead of us
         * we might want to sync.
         */
        fun potentiallyDoSync(): FlowStatus {
            var sameHeightCount = 0
            var higherHeightCount = 0
            for (ns in nodeStatuses) {
                if (ns.height == myStatus.height) sameHeightCount++
                else if (ns.height > myStatus.height) higherHeightCount++
            }
            if (sameHeightCount < this.quorum) {
                // cannot build a block

                // worth trying to sync?
                // if we are in prepared state, we fetch block only if there's a supermajority
                // of nodes with higher height
                if (higherHeightCount > 0) {
                    val _intent = intent

                    if (_intent is FetchBlockAtHeightIntent) {
                        if (_intent.height == myStatus.height)
                            return FlowStatus.Break
                        intent = FetchBlockAtHeightIntent(myStatus.height)
                        return FlowStatus.Continue
                    }

                    if (myStatus.state == NodeBlockState.HaveBlock) {
                        logger.warn("Resetting block in HaveBlock state")
                        resetBlock()
                    }

                    // try fetching a block
                    this.intent = FetchBlockAtHeightIntent(myStatus.height)
                    return FlowStatus.Continue
                }
            }
            return FlowStatus.JustRunOn
        }

        /**
         * Handles possible revolts
         */
        fun potentiallyRevolt(): FlowStatus {
            var nHighRound = 0
            var nRevolting = 0
            for (ns in nodeStatuses) {
                if (ns.height != myStatus.height) continue
                if (ns.round == myStatus.round) {
                    if (ns.revolting) nRevolting++
                } else if (ns.round > myStatus.round) {
                    nHighRound++
                }
            }
            if (nHighRound + nRevolting >= this.quorum) {
                // revolt is successful

                // Note: we do not reset block if NodeState is Prepared.
                if (myStatus.state == NodeBlockState.HaveBlock) {
                    resetBlock()
                }

                myStatus.revolting = false
                myStatus.round += 1
                myStatus.serial += 1
                return FlowStatus.Continue
            } else {
                return FlowStatus.JustRunOn
            }
        }

        /**
         * Takes care of the [HaveBlock] (second) state
         * Will move to state [Prepared] if enough nodes have reached our BlockRID
         */
        fun handleHaveBlockState(): Boolean {
            val count = countNodes(NodeBlockState.HaveBlock, myStatus.height, myStatus.blockRID) +
                    countNodes(NodeBlockState.Prepared, myStatus.height, myStatus.blockRID)
            if (count >= this.quorum) {
                myStatus.state = NodeBlockState.Prepared
                myStatus.serial += 1
                return true
            } else {
                return false
            }
        }


        /**
         * Takes care of the [Prepared] (last) state
         */
        fun handlePreparedState(): Boolean {
            if (intent is CommitBlockIntent) return false
            val count = commitSignatures.count { it != null }
            if (count >= this.quorum) {
                // check if we have (2f+1) commit signatures including ours, in that case we signal commit intent.
                intent = CommitBlockIntent
                logger.debug { "setting CommitBlockIntent for idx: $myIndex " }
                return true
            } else {
                // otherwise, we set intent to FetchCommitSignatureIntent with current blockRID and list of nodes which
                // are already in prepared state but don't have commit signatures in our array
                val unfetchedNodes = mutableListOf<Int>()
                for ((i, nodeStatus) in nodeStatuses.withIndex()) {
                    val commitSignature = commitSignatures[i]
                    if (commitSignature == null) {
                        if ((nodeStatus.height > myStatus.height)
                                ||
                                ((nodeStatus.height == myStatus.height)
                                        && (nodeStatus.state === NodeBlockState.Prepared)
                                        && nodeStatus.blockRID != null
                                        && nodeStatus.blockRID.contentEquals(myStatus.blockRID))) {
                            unfetchedNodes.add(i)
                        }
                    }
                }
                if (unfetchedNodes.isNotEmpty()) {
                    val newIntent = FetchCommitSignatureIntent(myStatus.blockRID as ByteArray, unfetchedNodes.toTypedArray())
                    if (newIntent == intent) {
                        return false
                    } else {
                        intent = newIntent
                        return true
                    }
                } else {
                    if (intent == DoNothingIntent)
                        return false
                    else {
                        intent = DoNothingIntent
                        return true
                    }
                }
            }
        }

        /**
         * Takes care of the [WaitBlock] (initial) state
         * If: I'm the "primary" node,
         *     Then: It will by my job to build the block (set intent to [BuildBlockIntent])
         * Else: some other node is building the block, I have to wait until they send me the block
         *       that the primary node has
         */
        fun handleWaitBlockState(): Boolean {
            if (isMyNodePrimary()) {
                if (intent !is BuildBlockIntent) {
                    intent = BuildBlockIntent
                    return true
                }
            } else {
                val primaryBlockRID = this.nodeStatuses[this.primaryIndex()].blockRID
                if (primaryBlockRID != null) {
                    val _intent = intent
                    if (!(_intent is FetchUnfinishedBlockIntent &&
                                    _intent.isThisTheBlockWeAreWaitingFor(myStatus.blockRID))) {
                        intent = FetchUnfinishedBlockIntent(primaryBlockRID)
                        return true
                    }
                } else {
                    if (intent == DoNothingIntent)
                        return false
                    else {
                        intent = DoNothingIntent
                        return true
                    }
                }
            }
            return false
        }

        // We should make sure we have enough nodes who can participate in building a block.
        // (If we are in [Prepared] state we ignore this check, it has been done before we got here)
        if (myStatus.state != NodeBlockState.Prepared) {
            when (potentiallyDoSync()) {
                FlowStatus.Break -> return false
                FlowStatus.Continue -> return true
                FlowStatus.JustRunOn -> Unit // nothing, just go on
            }
        }

        // Are we interested in checking for revolt?
        if (myStatus.revolting) {
            when (potentiallyRevolt()) {
                FlowStatus.Break -> return false
                FlowStatus.Continue -> return true
                FlowStatus.JustRunOn -> Unit // nothing, just go on
            }
        }

        // Handle the different states
        when (myStatus.state) {
            NodeBlockState.HaveBlock -> return handleHaveBlockState()
            NodeBlockState.Prepared -> return handlePreparedState()
            NodeBlockState.WaitBlock -> return handleWaitBlockState()
        }
    }

}
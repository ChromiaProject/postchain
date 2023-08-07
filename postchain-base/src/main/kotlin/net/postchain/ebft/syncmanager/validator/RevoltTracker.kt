// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.syncmanager.validator

import net.postchain.core.BlockchainEngine
import net.postchain.ebft.NodeStatus
import net.postchain.ebft.StatusManager
import kotlin.math.log
import kotlin.math.pow

open class RevoltTracker(
        private val statusManager: StatusManager,
        private val config: RevoltConfigurationData,
        engine: BlockchainEngine
) {
    private val blockBuildingStrategy = engine.getBlockBuildingStrategy()
    private val maxDelayRound = log(
            ((config.exponentialDelayMax + config.exponentialDelayBase) / config.exponentialDelayBase).toDouble(),
            DELAY_POWER_BASE
    ).toLong()
    private val initialHeight = statusManager.myStatus.height
    private var prevHeight = initialHeight
    private var prevRound = statusManager.myStatus.round
    var deadLine = newDeadLine(0)
        private set

    companion object {
        const val DELAY_POWER_BASE = 1.2
    }

    /**
     * Starts a revolt if certain conditions are met.
     */
    fun update() {
        val current = statusManager.myStatus
        if (fastRevolt(current)) return

        if (!shouldBuildBlock()) return

        if (current.height > prevHeight ||
                current.height == prevHeight && current.round > prevRound) {
            prevHeight = current.height
            prevRound = current.round
            deadLine = newDeadLine(current.round)
        } else if (currentTimeMillis() > deadLine && !current.revolting) {
            this.statusManager.onStartRevolting()
        }
    }

    /**
     * Set new deadline for the revolt tracker with exponential delay for each round
     *
     * @return the time at which the deadline is passed
     */
    private fun newDeadLine(round: Long): Long {
        val baseTimeout = currentTimeMillis() + config.timeout
        return if (round >= maxDelayRound) {
            baseTimeout + config.exponentialDelayMax
        } else {
            baseTimeout + (config.exponentialDelayBase * (DELAY_POWER_BASE.pow(round.toDouble()))).toLong() - config.exponentialDelayBase
        }
    }

    private fun fastRevolt(current: NodeStatus): Boolean =
            if (shouldDoFastRevolt(current)) {
                if (!current.revolting) statusManager.onStartRevolting()
                true
            } else
                false

    private fun shouldDoFastRevolt(current: NodeStatus): Boolean {
        // Check if fast revolt is enabled
        if (config.fastRevoltStatusTimeout < 0) return false

        if (current.height == initialHeight || statusManager.isMyNodePrimary()) return false

        val lastUpdateFromPrimary = statusManager.getLatestStatusTimestamp(statusManager.primaryIndex())
        return currentTimeMillis() - lastUpdateFromPrimary > config.fastRevoltStatusTimeout
    }

    private fun shouldBuildBlock(): Boolean = blockBuildingStrategy.shouldBuildBlock()

    protected open fun currentTimeMillis() = System.currentTimeMillis()
}
// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.syncmanager.validator

import net.postchain.core.BlockchainEngine
import net.postchain.ebft.NodeStatus
import net.postchain.ebft.StatusManager
import java.time.Clock
import kotlin.math.log
import kotlin.math.pow


class RevoltTracker(
        private val statusManager: StatusManager,
        private val config: RevoltConfigurationData,
        engine: BlockchainEngine,
        private val clock: Clock = Clock.systemUTC()
) {
    private val initialDelay = config.getInitialDelay()
    private val exponentialDelayPowerBase = config.getDelayPowerBase()
    private val blockBuildingStrategy = engine.getBlockBuildingStrategy()
    private val maxDelayRound = log(
            ((config.exponentialDelayMax + initialDelay) / initialDelay).toDouble(),
            exponentialDelayPowerBase
    ).toLong()
    private val initialHeight = statusManager.myStatus.height
    private var prevHeight = initialHeight
    private var prevRound = statusManager.myStatus.round
    var deadline = newDeadline(0)
        private set

    /**
     * Starts a revolt if certain conditions are met.
     */
    fun update() {
        val current = statusManager.myStatus
        if (fastRevolt(current)) return

        if (config.revoltWhenShouldBuildBlock && !shouldBuildBlock()) return

        if (current.height > prevHeight ||
                current.height == prevHeight && current.round > prevRound) {
            prevHeight = current.height
            prevRound = current.round
            deadline = newDeadline(current.round)
        } else if (currentTimeMillis() > deadline && !current.revolting) {
            this.statusManager.onStartRevolting()
        }
    }

    /**
     * Set new deadline for the revolt tracker with exponential delay for each round
     *
     * @return the time at which the deadline is passed
     */
    private fun newDeadline(round: Long): Long {
        val baseTimeout = currentTimeMillis() + config.timeout
        return if (round >= maxDelayRound) {
            baseTimeout + config.exponentialDelayMax
        } else {
            baseTimeout + (initialDelay * (exponentialDelayPowerBase.pow(round.toDouble()))).toLong() - initialDelay
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

    private fun currentTimeMillis() = clock.millis()
}
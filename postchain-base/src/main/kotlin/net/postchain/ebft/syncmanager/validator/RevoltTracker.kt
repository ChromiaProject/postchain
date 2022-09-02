// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.syncmanager.validator

import net.postchain.ebft.StatusManager
import java.util.*
import kotlin.math.log
import kotlin.math.pow

class RevoltTracker(private val statusManager: StatusManager, private val config: RevoltConfigurationData) {
    private val initialHeight = statusManager.myStatus.height
    private val maxDelayRound = log(
        ((config.exponentialDelayMax + config.exponentialDelayBase) / config.exponentialDelayBase).toDouble(),
        DELAY_POWER_BASE
    ).toLong()
    private var deadLine = newDeadLine(0)
    private var prevHeight = initialHeight
    private var prevRound = statusManager.myStatus.round

    companion object {
        const val DELAY_POWER_BASE = 1.2
    }

    /**
     * Set new deadline for the revolt tracker with exponential delay for each round
     *
     * @return the time at which the deadline is passed
     */
    private fun newDeadLine(round: Long): Long {
        val baseTimeout = Date().time + config.timeout
        return if (round >= maxDelayRound) {
            baseTimeout + config.exponentialDelayMax
        } else {
            baseTimeout + (config.exponentialDelayBase * (DELAY_POWER_BASE.pow(round.toDouble()))).toLong() - config.exponentialDelayBase
        }
    }

    /**
     * Starts a revolt if certain conditions are met.
     */
    fun update() {
        val current = statusManager.myStatus
        if (current.height > prevHeight ||
                current.height == prevHeight && current.round > prevRound) {
            prevHeight = current.height
            prevRound = current.round
            deadLine = newDeadLine(current.round)
        } else if ((Date().time > deadLine || shouldDoFastRevolt()) && !current.revolting) {
            this.statusManager.onStartRevolting()
        }
    }

    private fun shouldDoFastRevolt(): Boolean {
        // Check if fast revolt is enabled
        if (config.fastRevoltStatusTimeout < 0) return false

        val current = statusManager.myStatus
        if (current.height == initialHeight || statusManager.isMyNodePrimary()) return false

        val lastUpdateFromPrimary = statusManager.getLatestStatusTimestamp(statusManager.primaryIndex())
        return System.currentTimeMillis() - lastUpdateFromPrimary > config.fastRevoltStatusTimeout
    }
}
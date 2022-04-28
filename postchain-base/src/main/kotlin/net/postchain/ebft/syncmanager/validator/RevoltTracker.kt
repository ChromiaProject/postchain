// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.syncmanager.validator

import net.postchain.ebft.StatusManager
import java.util.*
import kotlin.math.pow

class RevoltTracker(private val revoltTimeout: Int, private val statusManager: StatusManager) {
    var deadLine = newDeadLine(0)
    var prevHeight = statusManager.myStatus.height
    var prevRound = statusManager.myStatus.round

    companion object {
        const val BASE_DELAY = 1_000
        const val DELAY_POWER_BASE = 2.0
    }

    /**
     * Set new deadline for the revolt tracker with exponential delay for each round
     *
     * @return the time at which the deadline is passed
     */
    private fun newDeadLine(round: Long): Long {
        return Date().time + revoltTimeout +
                (BASE_DELAY * (DELAY_POWER_BASE.pow(round.toDouble()))).toLong() - BASE_DELAY
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
        } else if (Date().time > deadLine && !current.revolting) {
            this.statusManager.onStartRevolting()
        }
    }
}
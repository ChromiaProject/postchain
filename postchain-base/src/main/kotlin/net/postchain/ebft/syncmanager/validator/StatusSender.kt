// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.syncmanager.validator

import net.postchain.ebft.StatusManager
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.Status
import net.postchain.network.CommunicationManager
import net.postchain.network.common.LazyPacket
import java.time.Clock
import java.util.Date

class StatusSender(
        private val maxStatusInterval: Int,
        private val statusManager: StatusManager,
        private val communicationManager: CommunicationManager<EbftMessage>,
        private val clock: Clock = Clock.systemUTC()
) {
    var lastSerial: Long = -1
    var lastSentTime: Long = Date(0L).time
    var previousMessage: Pair<Status, LazyPacket>? = null

    // Sends a status message to all peers when my status has changed or
    // after a timeout period.
    fun update() {
        val myStatus = statusManager.myStatus
        val isNewSerial = myStatus.serial > this.lastSerial
        val timeoutExpired = clock.millis() - this.lastSentTime > this.maxStatusInterval
        if (isNewSerial || timeoutExpired) {
            this.lastSentTime = Date().time
            this.lastSerial = myStatus.serial
            val statusMessage = Status(
                    myStatus.blockRID,
                    myStatus.height,
                    myStatus.revolting,
                    myStatus.round,
                    myStatus.serial,
                    myStatus.state.ordinal
            )

            previousMessage = previousMessage?.takeIf { !isNewSerial }?.apply { communicationManager.broadcastPacket(statusMessage, this.second) }
                    ?: (statusMessage to communicationManager.broadcastPacket(statusMessage))
        }
    }
}
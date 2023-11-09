// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.syncmanager.validator

import mu.withLoggingContext
import net.postchain.ebft.StatusManager
import net.postchain.ebft.message.Status
import net.postchain.ebft.worker.WorkerContext
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.network.common.LazyPacket
import java.time.Clock
import java.util.Date

class StatusSender(
        private val maxStatusInterval: Int,
        private val workerContext: WorkerContext,
        private val statusManager: StatusManager,
        private val clock: Clock = Clock.systemUTC()
) {
    private val communicationManager = workerContext.communicationManager
    private var lastSerial: Long = -1
    private var lastSentTime: Long = Date(0L).time
    private var previousMessage: Pair<Status, Map<Long, LazyPacket>>? = null

    // Sends a status message to all peers when my status has changed or
    // after a timeout period.
    fun update() {
        val myStatus = statusManager.myStatus
        val configHash = workerContext.blockchainConfiguration.configHash
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
                    myStatus.state.ordinal,
                    myStatus.signature,
                    configHash
            )

            withLoggingContext(
                    BLOCKCHAIN_RID_TAG to workerContext.blockchainConfiguration.blockchainRid.toHex(),
                    CHAIN_IID_TAG to workerContext.blockchainConfiguration.chainID.toString()
            ) {
                previousMessage = previousMessage?.takeIf { !isNewSerial }?.apply { communicationManager.broadcastPacket(statusMessage, this.second) }
                        ?: (statusMessage to communicationManager.broadcastPacket(statusMessage))
            }
        }
    }
}
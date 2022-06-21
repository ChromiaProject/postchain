package net.postchain.ebft.syncmanager.common

import mu.KLogging
import net.postchain.core.NodeRid

/**
 * Keeps track of peer's statuses. This type has one more status than the superclass:
 *
 * Drained: The peer's tip is reached.
 */
class FastSyncPeerStatuses(val params: FastSyncParameters): AbstractPeerStatuses<FastSyncKnownState>() {

    companion object : KLogging() {}

    fun drained(peerId: NodeRid, height: Long, now: Long) {
        val status = stateOf(peerId)
        if (status.isBlacklisted()) {
            logger.warn("We tried to get block from a blacklisted node: ${peerId.shortString()}, was it recently blacklisted?")
            return
        }
        if (logger.isDebugEnabled) {
            if (!status.isDrained(now)) { // Don't worry about resurrect b/c we drain later
                logger.debug("Setting new fast sync peer status DRAINED at height: $height")
            }
        }
        status.drained(height, now)
    }

    fun headerReceived(peerId: NodeRid, height: Long) {
        val status = stateOf(peerId)
        if (status.isBlacklisted()) {
            logger.warn("We got a header from a blacklisted node: ${peerId.shortString()}, was it recently blacklisted?")
            return
        }
        status.headerReceived(height)
    }

    fun statusReceived(peerId: NodeRid, height: Long) {
        val status = stateOf(peerId)
        if (status.isBlacklisted()) {
            logger.warn("Got status from a blacklisted node: ${peerId.shortString()}, was it recently blacklisted?")
            return
        }
        status.statusReceived(height)
    }


    override fun stateOf(peerId: NodeRid): FastSyncKnownState {
        return statuses.computeIfAbsent(peerId) { FastSyncKnownState(params) }
    }

}
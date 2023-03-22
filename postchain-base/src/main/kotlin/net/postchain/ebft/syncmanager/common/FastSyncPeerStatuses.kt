package net.postchain.ebft.syncmanager.common

import mu.KLogging
import net.postchain.core.NodeRid
import net.postchain.devtools.NameHelper

/**
 * Keeps track of peer's statuses. This type has one more status than the superclass:
 *
 * - Drained: The peer's tip is reached (In slow sync nodes cannot become "drained", we are meant to go on forever.
 *            But in fast sync we need "drained").
 */
class FastSyncPeerStatuses(val params: SyncParameters): AbstractPeerStatuses<FastSyncKnownState>() {

    companion object : KLogging()

    fun drained(peerId: NodeRid, height: Long, now: Long) {
        val status = stateOf(peerId)
        if (status.isBlacklisted()) {
            logger.warn("We tried to get block from a blacklisted node: ${NameHelper.peerName(peerId)}, was it recently blacklisted?")
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
            logger.warn("We got a header from a blacklisted node: ${NameHelper.peerName(peerId)}, was it recently blacklisted?")
            return
        }
        status.headerReceived(height)
    }

    fun statusReceived(peerId: NodeRid, height: Long) {
        val status = stateOf(peerId)
        if (status.isBlacklisted()) {
            logger.warn("Got status from a blacklisted node: ${NameHelper.peerName(peerId)}, was it recently blacklisted?")
            return
        }
        status.statusReceived(height)
    }


    override fun stateOf(peerId: NodeRid): FastSyncKnownState {
        return statuses.computeIfAbsent(peerId) { FastSyncKnownState(params) }
    }

}
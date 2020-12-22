package net.postchain.ebft.syncmanager.common

import mu.KLogging
import net.postchain.network.x.XPeerID

/**
 * Keeps track of peer's statuses. The currently trackeed statuses are
 *
 * Blacklisted: We have received invalid data from the peer, or it's otherwise misbehaving
 * Unresponsive: We haven't received a timely response from the peer
 * NotDrained: This class doesn't have any useful information about the peer
 * Drained: The peer's tip is reached.
 */
class PeerStatuses(val params: FastSyncParameters): KLogging() {

    /**
     * Keeps notes on a single peer. Some rules:
     *
     * When a peer has been marked DRAINED or UNRESPONSIVE for a certain
     * amount of time ([params.resurrectDrainedTime] and
     * [params.resurrectUnresponsiveTime] resp.) it will be given
     * a new chance to serve us blocks. Otherwise we might run out of
     * peers to sync from over time.
     *
     * Peers that are marked BLACKLISTED, should never be given another chance
     * because they have been proven to provide bad data (deliberately or not).
     *
     * The DRAINED state is reset to NOT_DRAINED whenever we receive a valid header for a
     * height higher than the height at which it was drained or when we
     * receive a Status message (which is sent regurarly from peers in normal
     * sync mode).
     *
     * We use Status messages as indication that there are headers
     * available at that Status' height-1 (The height in the Status
     * message indicates the height that they're working on, ie their committed
     * height + 1). They also serve as a discovery mechanism, in which we become
     * aware of our neiborhood.
     */
    private class KnownState(val params: FastSyncParameters) {
        private enum class State {
            BLACKLISTED, UNRESPONSIVE, NOT_DRAINED, DRAINED
        }
        private var state = State.NOT_DRAINED

        private var unresponsiveTime: Long = System.currentTimeMillis()
        private var drainedTime: Long = System.currentTimeMillis()
        private var drainedHeight: Long = -1

        fun isBlacklisted() = state == State.BLACKLISTED
        fun isUnresponsive() = state == State.UNRESPONSIVE
        private fun isDrained() = state == State.DRAINED
        fun isDrained(h: Long) = isDrained() && drainedHeight < h

        fun drained(height: Long) {
            state = State.DRAINED
            if (height > drainedHeight) {
                drainedHeight = height
                drainedTime = System.currentTimeMillis()
            }
        }
        fun headerReceived(height: Long) {
            if (state == State.DRAINED && height > drainedHeight) {
                state = State.NOT_DRAINED
            }
        }
        fun statusReceived(height: Long) {
            // We take a Status message as an indication that
            // there might be more blocks to fetch now. But
            // we won't resurrect unresponsive peers.
            if (state == State.DRAINED && height > drainedHeight) {
                state = State.NOT_DRAINED
            }
        }
        fun unresponsive() {
            if (this.state != State.UNRESPONSIVE) {
                this.state = State.UNRESPONSIVE
                unresponsiveTime = System.currentTimeMillis()
            }
        }
        fun blacklist() {
            this.state = State.BLACKLISTED
        }
        fun resurrect(now: Long) {
            if (isDrained() && drainedTime + params.resurrectDrainedTime < now ||
                    isUnresponsive() && unresponsiveTime + params.resurrectUnresponsiveTime < now) {
                state = State.NOT_DRAINED
            }
        }
    }
    private val statuses = HashMap<XPeerID, KnownState>()

    private fun resurrectDrainedAndUnresponsivePeers() {
        val now = System.currentTimeMillis()
        statuses.forEach {
            it.value.resurrect(now)
        }
    }

    fun exclDrainedAndUnresponsive(height: Long): Set<XPeerID> {
        resurrectDrainedAndUnresponsivePeers()
        return statuses.filterValues { it.isDrained(height) || it.isUnresponsive() || it.isBlacklisted() }.keys
    }

    fun drained(peerId: XPeerID, height: Long) {
        val status = stateOf(peerId)
        if (status.isBlacklisted()) {
            return
        }
        status.drained(height)
    }

    fun headerReceived(peerId: XPeerID, height: Long) {
        val status = stateOf(peerId)
        if (status.isBlacklisted()) {
            return
        }
        status.headerReceived(height)
    }

    fun statusReceived(peerId: XPeerID, height: Long) {
        val status = stateOf(peerId)
        if (status.isBlacklisted()) {
            return
        }
        status.statusReceived(height)
    }

    fun unresponsive(peerId: XPeerID) {
        val status = stateOf(peerId)
        if (status.isBlacklisted()) {
            return
        }
        status.unresponsive()
    }

    fun blacklist(peerId: XPeerID) {
        stateOf(peerId).blacklist()
    }

    private fun stateOf(peerId: XPeerID): KnownState {
        return statuses.computeIfAbsent(peerId) { KnownState(params) }
    }

    /**
     * Adds the peer if it doesn't exist. Do nothing if it exists.
     */
    fun addPeer(peerId: XPeerID) {
        stateOf(peerId)
    }

    fun isBlacklisted(xPeerId: XPeerID): Boolean {
        return stateOf(xPeerId).isBlacklisted()
    }

    fun countDrained(height: Long): Int {
        return statuses.count { it.value.isDrained(height) }
    }

    fun countAll(): Int {
        return statuses.size
    }

    fun clear() {
        statuses.clear()
    }
}
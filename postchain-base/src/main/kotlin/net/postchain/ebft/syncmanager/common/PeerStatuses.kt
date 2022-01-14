package net.postchain.ebft.syncmanager.common

import mu.KLogging
import net.postchain.core.NodeRid

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
     * The DRAINED state is reset to SYNCABLE whenever we receive a valid header for a
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
    class KnownState(val params: FastSyncParameters): KLogging() {
        private enum class State {
            BLACKLISTED, UNRESPONSIVE, SYNCABLE, DRAINED
        }

        private var state = State.SYNCABLE

        /**
         * [maybeLegacy] and [confirmedModern] are transitional and should be
         * removed once most nodes have upgraded, because then
         * nodes will be able to sync from modern nodes and we no longer
         * need to be able to sync from old nodes.
         */
        private var maybeLegacy = false
        private var confirmedModern = false
        private var unresponsiveTime: Long = 0
        private var drainedTime: Long = 0
        private var drainedHeight: Long = -1
        private var errorCount = 0
        private var timeOfLastError: Long = 0

        fun isBlacklisted() = isBlacklisted(System.currentTimeMillis())
        fun isBlacklisted(now: Long): Boolean {
            if (state == State.BLACKLISTED && (now > timeOfLastError + params.blacklistingTimeoutMs)) {
                // Alex suggested that peers should be given new chances often
                if (logger.isDebugEnabled) {
                    logger.debug("Peer timed out of blacklist")
                }
                this.state = State.SYNCABLE
                this.timeOfLastError = 0
                this.errorCount = 0
            }

            return state == State.BLACKLISTED
        }

        fun isUnresponsive(now: Long): Boolean {
            if (state == State.UNRESPONSIVE && (now > unresponsiveTime + params.resurrectUnresponsiveTime)) {
                if (logger.isDebugEnabled) {
                    logger.debug("Peer timed out of unresponsiveness")
                }
                this.state = State.SYNCABLE
                this.unresponsiveTime = 0
            }

            return state == State.UNRESPONSIVE
        }

        fun isDrained(now: Long): Boolean {
            if (state == State.DRAINED && (now > drainedTime + params.resurrectDrainedTime)) {
                if (logger.isDebugEnabled) {
                    logger.debug("Peer timed out of drained")
                }
                this.state = State.SYNCABLE
                this.drainedTime = 0
                this.drainedHeight = -1
            }

            return state == State.DRAINED
        }

        fun isMaybeLegacy() = !confirmedModern && maybeLegacy
        fun isConfirmedModern() = confirmedModern
        fun isSyncable(h: Long) = state == State.SYNCABLE || state == State.DRAINED && drainedHeight >= h

        /**
         * @param height is where the node's highest block can be found (but higher than that the node has no blocks).
         */
        fun drained(height: Long, now: Long) {
            state = State.DRAINED
            drainedTime = now
            if (height > drainedHeight) {
                drainedHeight = height
            }
        }

        fun headerReceived(height: Long) {
            if (state == State.DRAINED && height > drainedHeight) {
                if (logger.isDebugEnabled) {
                    logger.debug("Got header. Setting new fast sync peer status SYNCABLE")
                }
                state = State.SYNCABLE
            }
        }
        fun statusReceived(height: Long) {
            // We take a Status message as an indication that
            // there might be more blocks to fetch now. But
            // we won't resurrect unresponsive peers.
            if (state == State.DRAINED && height > drainedHeight) {
                if (logger.isDebugEnabled) {
                    logger.debug("Got status. Setting new fast sync peer status SYNCABLE")
                }
                state = State.SYNCABLE
            }
        }

        /**
         * Note: this will get into conflict with connection manager, which also has a way of dealing with
         * unresponsive peers.
         */
        fun unresponsive(desc: String, now: Long) {
            if (this.state != State.UNRESPONSIVE) {
                this.state = State.UNRESPONSIVE
                unresponsiveTime = now
                if (logger.isDebugEnabled) {
                    logger.debug("Setting new fast sync peer status UNRESPONSIVE: $desc")
                }
            }
        }

        fun maybeLegacy(isLegacy: Boolean) {
            if (!this.confirmedModern) {
                if (this.maybeLegacy != isLegacy) {
                    if (logger.isDebugEnabled) {
                        logger.debug("Setting new fast sync peer status maybeLegacy: $isLegacy")
                    }
                }
                this.maybeLegacy = isLegacy
            }
        }
        fun confirmedModern() {
            if (logger.isDebugEnabled) {
                if (!this.confirmedModern) {
                    logger.debug("Setting new fast sync peer status CONFIRMED MODERN")
                }
            }
            this.confirmedModern = true
            this.maybeLegacy = false
        }

        fun blacklist(desc: String, now: Long) {
            if (this.state != State.BLACKLISTED) {
                errorCount++
                if (errorCount >= params.maxErrorsBeforeBlacklisting) {
                    logger.warn("Blacklisting peer: $desc")
                    this.state = State.BLACKLISTED
                    this.timeOfLastError = now
                } else {
                    if (logger.isTraceEnabled) {
                        logger.trace("Not blacklisting peer: $desc")
                    }
                    this.timeOfLastError = now
                }
            }
        }

        fun resurrect(now: Long) {
            isUnresponsive(now)
            isDrained(now)
        }

        override fun toString(): String {
            return "state: ${state.name}, legacy: $maybeLegacy, modern: $confirmedModern"
        }
    }

    private val statuses = HashMap<NodeRid, KnownState>()

    private fun resurrectDrainedAndUnresponsivePeers(now: Long) {
        statuses.forEach {
            it.value.resurrect(now)
        }
    }

    /**
     * @param height is the height we need
     * @param now is our current time (we want to send it to keep this pure = testable)
     * @return the nodes we SHOULDN'T sync
     */
    fun exclNonSyncable(height: Long, now: Long): Set<NodeRid> {
        resurrectDrainedAndUnresponsivePeers(now)
        val excluded = statuses.filter {
            val state = it.value
            val syncable = state.isSyncable(height) && !state.isMaybeLegacy()
            !syncable
        }
        return excluded.keys

    }

    fun getLegacyPeers(height: Long): Set<NodeRid> {
        return statuses.filterValues { it.isMaybeLegacy() && it.isSyncable(height) }.keys
    }

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

    /**
     * @param peerId is the peer that's not responding
     * @param desc is the text we will log, surrounding the circumstances.
     *             (This could be caused by a bug, if so it has to be traced)
     */
    fun unresponsive(peerId: NodeRid, desc: String) {
        val status = stateOf(peerId)
        if (status.isBlacklisted()) {
            return
        }
        status.unresponsive(desc, System.currentTimeMillis())
    }

    fun setMaybeLegacy(peerId: NodeRid, isLegacy: Boolean) {
        val status = stateOf(peerId)
        if (status.isBlacklisted()) {
            return
        }
        if (logger.isDebugEnabled) {
            if (status.isMaybeLegacy() != isLegacy) {
                logger.debug("Setting new fast sync peer: ${peerId.shortString()} status maybe legacy: $isLegacy.")
            }
        }
        status.maybeLegacy(isLegacy)
    }

    fun isMaybeLegacy(peerId: NodeRid): Boolean {
        return stateOf(peerId).isMaybeLegacy()
    }
    fun isConfirmedModern(peerId: NodeRid): Boolean {
        return stateOf(peerId).isConfirmedModern()
    }
    fun confirmModern(peerId: NodeRid) {
        stateOf(peerId).confirmedModern()
    }

    /**
     * Might blacklist this peer depending on number of failures.
     *
     * @param peerId is the peer that's behaving badly
     * @param desc is the text we will log, surrounding the circumstances.
     *             (This could be caused by a bug, if so it has to be traced)
     */
    fun maybeBlacklist(peerId: NodeRid, desc: String) {
        stateOf(peerId).blacklist(desc, System.currentTimeMillis())
    }

    private fun stateOf(peerId: NodeRid): KnownState {
        return statuses.computeIfAbsent(peerId) { KnownState(params) }
    }

    /**
     * Adds the peer if it doesn't exist. Do nothing if it exists.
     */
    fun addPeer(peerId: NodeRid) {
        stateOf(peerId)
    }

    fun isBlacklisted(xPeerId: NodeRid): Boolean {
        return stateOf(xPeerId).isBlacklisted()
    }

    fun getSyncable(height: Long): Set<NodeRid> {
        return statuses.filterValues { it.isSyncable(height) }.map {it.key}.toSet()
    }

    fun clear() {
        if (logger.isDebugEnabled) {
            logger.debug("clearing all fast sync peer statuses")
        }
        statuses.clear()
    }
}

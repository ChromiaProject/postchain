package net.postchain.ebft.syncmanager.common

import mu.KLogging

/**
 * Keeps notes on a single peer. Some rules:
 *
 * When a peer has been marked UNRESPONSIVE for a certain
 * amount of time ([params.resurrectDrainedTime] it will be given
 * a new chance to serve us blocks. Otherwise we might run out of
 * peers to sync from over time.
 *
 * Peers that are marked BLACKLISTED, should never be given another chance
 * because they have been proven to provide bad data (deliberately or not).
 *
 * We use Status messages as indication that there are headers
 * available at that Status' height-1 (The height in the Status
 * message indicates the height that they're working on, ie their committed
 * height + 1). They also serve as a discovery mechanism, in which we become
 * aware of our neighborhood.
 */
open class KnownState(val params: SyncParameters) {

    companion object: KLogging() {}

    protected enum class State {
        BLACKLISTED, UNRESPONSIVE, SYNCABLE, DRAINED
    }

    protected var state = State.SYNCABLE

    /**
     * [maybeLegacy] and [confirmedModern] are transitional and should be
     * removed once most nodes have upgraded, because then
     * nodes will be able to sync from modern nodes and we no longer
     * need to be able to sync from old nodes.
     */
    private var maybeLegacy = false
    private var confirmedModern = false
    private var unresponsiveTime: Long = 0

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



    fun isMaybeLegacy() = !confirmedModern && maybeLegacy
    fun isConfirmedModern() = confirmedModern
    open fun isSyncable(h: Long) = state == State.SYNCABLE || state == State.DRAINED // We don't mind "drained"

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

    open fun resurrect(now: Long) {
        isUnresponsive(now)
    }

    override fun toString(): String {
        return "state: ${state.name}, legacy: $maybeLegacy, modern: $confirmedModern"
    }
}
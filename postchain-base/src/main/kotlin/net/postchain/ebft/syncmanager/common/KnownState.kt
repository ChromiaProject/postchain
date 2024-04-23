package net.postchain.ebft.syncmanager.common

import mu.KLogging
import java.time.Clock

/**
 * Keeps notes on a single peer. Some rules:
 *
 * When a peer has been marked UNRESPONSIVE for a certain
 * amount of time ([params.resurrectDrainedTime] it will be given
 * a new chance to serve us blocks. Otherwise, we might run out of
 * peers to sync from over time.
 *
 * Peers that are marked BLACKLISTED should never be given another chance
 * because they have been proven to provide bad data (deliberately or not).
 *
 * We use Status messages as an indication that there are headers
 * available at that Status' height-1 (The height in the Status
 * message indicates the height that they're working on, i.e., their committed
 * height + 1). They also serve as a discovery mechanism, in which we become
 * aware of our neighborhood.
 */
class KnownState(val params: SyncParameters, val clock: Clock = Clock.systemUTC()) {

    companion object : KLogging()

    enum class State {
        BLACKLISTED, UNRESPONSIVE, SYNCABLE, DRAINED
    }

    var state = State.SYNCABLE
        protected set

    /**
     * [maybeLegacy] and [confirmedModern] are transitional and should be
     * removed once most nodes have upgraded, because then
     * nodes will be able to sync from modern nodes and we no longer
     * need to be able to sync from old nodes.
     */
    private var maybeLegacy = false
    private var confirmedModern = false
    private var unresponsiveTime: Long = 0

    // Queue containing times in ms, when errors have occurred.
    private val errors = java.util.ArrayDeque<Long>()

    private var timeOfLastError: Long = 0

    private var disconnectedSince: Long = 0

    private var drainedResurrectTime: Long = 0
    private var drainedHeight: Long = -1

    private fun currentTimeMillis() = clock.millis()

    fun isDrained(now: Long): Boolean {
        if (state == State.DRAINED && (now > drainedResurrectTime)) {
            if (logger.isDebugEnabled) {
                logger.debug("Peer timed out of drained")
            }
            this.state = State.SYNCABLE
            this.drainedResurrectTime = 0
            this.drainedHeight = -1
        }

        return state == State.DRAINED
    }

    /**
     * @param height is where the node's highest block can be found (but higher than that the node has no blocks).
     */
    fun drained(height: Long, now: Long, drainedTimeout: Long = params.resurrectDrainedTime) {
        state = State.DRAINED
        drainedResurrectTime = now + drainedTimeout
        if (height > drainedHeight) {
            drainedHeight = height
        }
    }

    fun isSyncable(h: Long) = state == State.SYNCABLE || state == State.DRAINED && drainedHeight >= h

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

    fun updateAndCheckBlacklisted() = updateAndCheckBlacklisted(currentTimeMillis())

    fun updateAndCheckBlacklisted(now: Long): Boolean {
        if (state == State.BLACKLISTED && (now > timeOfLastError + params.blacklistingTimeoutMs)) {
            // Alex suggested that peers should be given new chances often
            if (logger.isDebugEnabled) {
                logger.debug("Peer timed out of blacklist")
            }
            markAsSyncable()
        }

        return state == State.BLACKLISTED
    }

    fun markAsSyncable() {
        this.state = State.SYNCABLE
        this.timeOfLastError = 0
        this.errors.clear()
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
        addError(now)
        if (shouldBlacklist()) {
            logger.warn { "Blacklisting peer: $desc" }
            this.state = State.BLACKLISTED
        } else if (shouldUnBlacklist()) {
            logger.info { "Un-blacklisting peer: $desc" }
            this.state = State.SYNCABLE
        } else {
            logger.trace { "Not blacklisting peer: $desc" }
        }
        this.timeOfLastError = now
    }

    private fun shouldUnBlacklist() = this.state == State.BLACKLISTED && errors.size < params.maxErrorsBeforeBlacklisting

    private fun shouldBlacklist() = this.state != State.BLACKLISTED && errors.size >= params.maxErrorsBeforeBlacklisting

    private fun addError(now: Long) {
        errors.removeIf { now > it + params.blacklistingErrorTimeoutMs }
        if (errors.size == params.maxErrorsBeforeBlacklisting) errors.pop()
        errors.push(now)
    }

    fun disconnected(now: Long) {
        if (disconnectedSince == 0L) {
            disconnectedSince = now
        }
    }

    fun connected() {
        disconnectedSince = 0
    }

    fun isConnected(now: Long): Boolean {
        return disconnectedSince == 0L || now - disconnectedSince < params.disconnectTimeout
    }

    fun resurrect(now: Long) {
        isDrained(now)
        isUnresponsive(now)
        updateAndCheckBlacklisted(now)
    }

    override fun toString(): String {
        return "state: ${state.name}, legacy: $maybeLegacy, modern: $confirmedModern"
    }
}
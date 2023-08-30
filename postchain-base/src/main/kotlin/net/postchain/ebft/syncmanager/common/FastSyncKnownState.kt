package net.postchain.ebft.syncmanager.common

/**
 * Keeps notes on a single peer.
 * Fast Sync has to deal with drained nodes. Some rules:
 *
 * When a peer has been marked DRAINED for a certain
 * amount of time [params.resurrectUnresponsiveTime] resp.) it will be given
 * a new chance to serve us blocks. Otherwise we might run out of
 * peers to sync from over time.
 *
 * The DRAINED state is reset to SYNCABLE whenever we receive a valid header for a
 * height higher than the height at which it was drained or when we
 * receive a Status message (which is sent regularly from peers in normal
 * sync mode).
 */
class FastSyncKnownState(prms: SyncParameters) : KnownState(prms)  {

    private var drainedTime: Long = 0
    private var drainedHeight: Long = -1

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

    override fun isSyncable(h: Long) = state == State.SYNCABLE || state == State.DRAINED && drainedHeight >= h

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

    override fun resurrect(now: Long) {
        super.resurrect(now)
        isDrained(now)
    }
}
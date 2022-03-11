package net.postchain.ebft.syncmanager.common

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PeerStatusesKnownStateTest {


    @Test
    fun test_drained() {
        val params = FastSyncParameters()
        val state = PeerStatuses.KnownState(params)

        // Initial state
        assertTrue(state.isSyncable(1))

        val now = 1L
        state.drained(3, now)
        assertTrue(state.isSyncable(1)) // Works
        assertTrue(state.isSyncable(2)) // Works
        assertTrue(state.isSyncable(3)) // According to def, this works
        assertFalse(state.isSyncable(4)) // Fails
        assertFalse(state.isSyncable(5)) // Shouln't work either

        state.drained(7, now + 1)
        assertTrue(state.isSyncable(1)) // Still works
        assertTrue(state.isSyncable(7)) // Now this works
        assertFalse(state.isSyncable(8)) // Fails
    }

    @Test
    fun test_resurrect_after_unresponsive() {
        val params = FastSyncParameters()
        val state = PeerStatuses.KnownState(params)

        // Initial state
        assertTrue(state.isSyncable(1))
        var now = 7L
        assertFalse(state.isUnresponsive(now))

        state.unresponsive("Bad node", now)

        val expectedTimeout = params.resurrectUnresponsiveTime + now // This is when we will get it back

        assertTrue(state.isUnresponsive(now))
        assertTrue(state.isUnresponsive(now + 1))
        assertTrue(state.isUnresponsive(expectedTimeout - 1))


        assertFalse(state.isUnresponsive(expectedTimeout + 1)) // Now we timed out
        assertTrue(state.isSyncable(1)) // Back to being syncable
    }

    /**
     * This is a scenario where blacklist a peer, make it time out, and blacklist it again later.
     */
    @Test
    fun test_blacklist_and_timeout() {
        val params = FastSyncParameters()
        val state = PeerStatuses.KnownState(params)

        // Initial state
        assertTrue(state.isSyncable(1))

        var timeIter = makePeerBlacklisted(state)
        assertFalse(state.isSyncable(1)) //
        assertTrue(state.isBlacklisted(timeIter)) // 10 blacklist calls should make it blacklisted
        val expectedTimeout = params.blacklistingTimeoutMs + timeIter // This is when we will get it back

        // Wait one millisecond
        assertTrue(state.isBlacklisted(timeIter + 1))

        // Wait some time
        val wait = params.blacklistingTimeoutMs / 2
        assertTrue(state.isBlacklisted(timeIter + wait)) // Still blacklisted

        // Wait until the exact right time for release
        assertFalse(state.isBlacklisted( expectedTimeout + 1)) // Now released from blacklist
        assertTrue(state.isSyncable(1)) // Syncable again

        // We have to make sure it works again, but at some later time
        val currentTime = timeIter + expectedTimeout * 3;
        timeIter = makePeerBlacklisted(state, currentTime)
        assertFalse(state.isSyncable(1))
        assertTrue(state.isBlacklisted(timeIter))

    }

    private fun makePeerBlacklisted(state: PeerStatuses.KnownState, startTime: Long = 0L): Long {
        // Work up until blacklist
        var timeIter: Long = 1 + startTime
        while (timeIter < (10 + startTime)) {
            state.blacklist("Olle doesn't like this peer", timeIter)
            assertTrue(state.isSyncable(1))
            timeIter++
        }

        // Last straw that breaks the camel's back
        state.blacklist("Olle REALLY doesn't like this peer", timeIter)
        return timeIter
    }
}
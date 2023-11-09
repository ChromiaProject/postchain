package net.postchain.ebft.syncmanager.common

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class FastSyncPeerStatusesKnownStateTest {

    @Test
    fun test_drained() {
        val params = SyncParameters()
        val state = FastSyncKnownState(params)

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
        val params = SyncParameters()
        val state = FastSyncKnownState(params)

        // Initial state
        assertTrue(state.isSyncable(1))
        val now = 7L
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
        val params = SyncParameters()
        val state = FastSyncKnownState(params)

        // Initial state
        assertTrue(state.isSyncable(1))

        var timeIter = makePeerBlacklisted(state)
        assertFalse(state.isSyncable(1)) //
        assertTrue(state.updateAndCheckBlacklisted(timeIter)) // 10 blacklist calls should make it blacklisted
        val expectedTimeout = params.blacklistingTimeoutMs + timeIter // This is when we will get it back

        // Wait one millisecond
        assertTrue(state.updateAndCheckBlacklisted(timeIter + 1))

        // Wait some time
        val wait = params.blacklistingTimeoutMs / 2
        assertTrue(state.updateAndCheckBlacklisted(timeIter + wait)) // Still blacklisted

        // Wait until the exact right time for release
        assertFalse(state.updateAndCheckBlacklisted(expectedTimeout + 1)) // Now released from blacklist
        assertTrue(state.isSyncable(1)) // Syncable again

        // We have to make sure it works again, but at some later time
        val currentTime = timeIter + expectedTimeout * 3
        timeIter = makePeerBlacklisted(state, currentTime)
        assertFalse(state.isSyncable(1))
        assertTrue(state.updateAndCheckBlacklisted(timeIter))
    }

    @Test
    fun `error should only persist until error timeout is met`() {
        val params = SyncParameters()
        params.maxErrorsBeforeBlacklisting = 3
        params.blacklistingErrorTimeoutMs = 10
        val state = FastSyncKnownState(params)

        // Initial state
        assertTrue(state.isSyncable(1))

        // Induce max errors
        repeat(params.maxErrorsBeforeBlacklisting) {
            state.blacklist("Bad peer", it.toLong())
        }
        assertFalse(state.isSyncable(1)) //
        assertTrue(state.updateAndCheckBlacklisted(1))

        // More errors should still yield blacklisted status
        state.blacklist("Bad peer", 4)
        assertFalse(state.isSyncable(1)) //
        assertTrue(state.updateAndCheckBlacklisted(1))

        // When blacklistingErrorTimeoutMs time has passed for an error remove it
        // First 2 errors are removed since time has passed and one new is added => Under blacklist limit
        state.blacklist("Bad peer", params.blacklistingErrorTimeoutMs + 2) //
        assertTrue(state.isSyncable(1)) //
        assertFalse(state.updateAndCheckBlacklisted(1))

        // And back to blacklisted
        repeat(params.maxErrorsBeforeBlacklisting) {
            state.blacklist("Bad peer", it.toLong() + params.blacklistingErrorTimeoutMs + 5)
        }
        assertFalse(state.isSyncable(1)) //
        assertTrue(state.updateAndCheckBlacklisted(1))
    }

    private fun makePeerBlacklisted(state: FastSyncKnownState, startTime: Long = 0L): Long {
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
package net.postchain.ebft.syncmanager.common

import net.postchain.core.NodeRid
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Note: This test actually tests some slow sync code too, which works the same but without DRAINED.
 */
class FastSyncPeerStatusesTest {

    lateinit var fsps: FastSyncPeerStatuses
    val peer1 = p(1)
    val peer2 = p(2)

    val currentHeight = 1L
    val now = 1L

    private fun before() {

        val params = SyncParameters()
        fsps = FastSyncPeerStatuses(params)

        fsps.addPeer(peer1)
        fsps.addPeer(peer2)
    }

    private fun p(s: Int): NodeRid {
        return NodeRid(byteArrayOf(s.toByte()))
    }

    /**
     * Stupid happy test proving we can sync from all nodes
     */
    @Test
    fun happy_exclude_non_syncable_every_node_is_ok() {
        before()

        val nonSyncables: Set<NodeRid> = fsps.excludedNonSyncable(currentHeight, now)

        Assertions.assertEquals(0, nonSyncables.size)
    }

    /**
     * Peer1 cannot be synced from, while Peer2 is only "drained" at our current height (which is ok).
     */
    @Test
    fun exclude_non_syncable_unresponsive() {
        before()

        fsps.unresponsive(peer1, "Nooo!") // Unresponsive means "not syncable"

        val nonSyncables: Set<NodeRid> = fsps.excludedNonSyncable(currentHeight, now)

        Assertions.assertEquals(1, nonSyncables.size)
        Assertions.assertTrue(nonSyncables.contains(peer1))
    }

    /**
     * Peer2 is only "drained" at a future height (which means it's syncable).
     */
    @Test
    fun exclude_non_syncable_drained_at_current_height() {
        before()

        val drainedHeight = currentHeight + 1L // A drained node is still considered syncable if drained above our current height
        fsps.drained(peer2, drainedHeight, now)

        val nonSyncables: Set<NodeRid> = fsps.excludedNonSyncable(currentHeight, now + 1)

        Assertions.assertEquals(0, nonSyncables.size)
    }

    /**
     * Peer2 is "drained" below our current height -> there's nothing to get from it.
     */
    @Test
    fun exclude_non_syncable_one_node_is_syncable() {
        before()

        val drainedHeight = currentHeight - 1L // Node is drained below our current height -> not syncable
        fsps.drained(peer2, drainedHeight, now)

        val nonSyncables: Set<NodeRid> = fsps.excludedNonSyncable(currentHeight, now + 1)

        Assertions.assertEquals(1, nonSyncables.size)
        Assertions.assertTrue(nonSyncables.contains(peer2))
    }

}
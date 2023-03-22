package net.postchain.integrationtest.sync

import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.BlockchainRid
import net.postchain.core.AppContext
import net.postchain.crypto.PubKey
import net.postchain.devtools.AbstractSyncTest
import org.awaitility.Awaitility
import org.awaitility.core.ConditionTimeoutException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit

/* One signer, two replica nodes. After one block, node 0 (signer is turned off).
 * Node 1 (a replica) is wiped. Need node 2 (the other replica) to be able to sync.
 */
class ReplicaSyncTestNightly : AbstractSyncTest() {

    private companion object : KLogging()

    private var addReplica = false


    /**
     * Try to synchronize when the replica nodes have been removed from the blockchain_replicas table.
     *
     * In this test we only have one peer (Id = 0), but two replicas.
     *
     * NOTE: Olle: This test is somewhat broken, since we expect a timeout but don't check where the timeout actually happened
     *             I assume the timeout should happen at step 4, since we cannot sync from Replica 2.
     *
     */
    @Test
    @Timeout(5, unit = TimeUnit.MINUTES)
    fun testRemove() {
        assertThrows<ConditionTimeoutException> {
            try {
                Awaitility.await().atMost(30, TimeUnit.SECONDS).until { // Slower machines cannot finish on 7 secs.
                    runSyncTest(1, 2, setOf(1), setOf(0), 1)
                    true
                }
            } catch (e: ConditionTimeoutException) {
                logger.debug("++ Timeout ++")
                throw e
            }
        }
    }

    /**
     * Check that sync problem is solved if nodes are added to the blockchain replica table again
     *
     * In this test we only have one peer (Id = 0), but two replicas.
     *
     * NOTE: The difference from the test above is that we use "addReplica =  true" to add info about the
     *       replica by inserting into "blockchain_replicas" for the wiped Replica 1.
     *       Therefore in step 4, replica 1 can sync from replica 2.
     *
     * 1. run peer 0 [03A3:70]/03A30169 and replicas (1,2) up to the height= 0
     * 2. stop peer 0 ("stopIndex" list)
     * 3. kill replica 1 [03B8:A5]/03B82A54 ("syncIndex" list) and wipe the DB.
     * 4. wait until replica 1 gets back to the height they had (by syncing from replica 2 via fastsync)
     * 5. start peer 0
     * 6. build one more block and wait until all nodes have it.
     */
    @Test
    @Timeout(2, unit = TimeUnit.MINUTES)
    fun testRemoveAndAddAgain() {
        addReplica = true
        runSyncTest(1, 2, setOf(1), setOf(0), 1)
    }

    @Test
    @Timeout(2, unit = TimeUnit.MINUTES)
    fun testRemoveAndAddAgainWithForcedFastSync() {
        configOverrides.setProperty("slowsync.enabled", "false")
        addReplica = true
        runSyncTest(1, 2, setOf(1), setOf(0), 1)
    }

    override fun addPeerInfo(dbAccess: DatabaseAccess, ctx: AppContext, peerInfo: PeerInfo, brid: BlockchainRid, isPeerSigner: Boolean) {
        dbAccess.addPeerInfo(ctx, peerInfo)
        if (!isPeerSigner && addReplica) {
            dbAccess.addBlockchainReplica(ctx, brid, PubKey(peerInfo.pubKey))
        }
    }
}

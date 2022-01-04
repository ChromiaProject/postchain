package net.postchain.integrationtest.sync

import mu.KLogging
import net.postchain.core.BlockchainRid
import net.postchain.base.PeerInfo
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.toHex
import net.postchain.core.AppContext
import org.awaitility.Awaitility
import org.awaitility.core.ConditionTimeoutException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit

/* One signer, two replica nodes. After one block, node 0 (signer is turned off).
 * Node 1 (a replica) is wiped. Need node 2 (the other replica) to be able to sync.
 */
class ReplicaSyncTest : AbstractSyncTest() {

    private companion object: KLogging()
    private var addReplica = false


    // Try to synchronize when the replica nodes have been removed from the blockchain_replicas table.
    @Test
    fun testRemove() {
        assertThrows<ConditionTimeoutException> {
            Awaitility.await().atMost(30, TimeUnit.SECONDS).until { // Slower machines cannot finish on 7 secs.
                runSyncTest(1, 2, setOf(1), setOf(0), 1)
                true
            }
        }
    }

    // Check that sync problem is solved if nodes are added to the blockchain replica table again
    @Test
    fun testRemoveAndAddAgain() {
        addReplica = true
        runSyncTest(1, 2, setOf(1), setOf(0), 1)
    }

    override fun addPeerInfo(dbAccess: DatabaseAccess, ctx: AppContext, peerInfo: PeerInfo, brid: BlockchainRid, isPeerSigner: Boolean) {
        dbAccess.addPeerInfo(ctx, peerInfo)
        if (!isPeerSigner && addReplica) {
            dbAccess.addBlockchainReplica(ctx, brid.toHex(), peerInfo.pubKey.toHex())
        }
    }
}
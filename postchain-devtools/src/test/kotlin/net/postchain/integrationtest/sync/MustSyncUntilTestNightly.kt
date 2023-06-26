package net.postchain.integrationtest.sync

import mu.KLogging
import net.postchain.devtools.AbstractSyncTest
import net.postchain.devtools.currentHeight
import org.awaitility.Awaitility
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals

/* Two signers, no replica nodes.
 * After two blocks, Node 0 is wiped and starts to sync (from node 1)
 */
class MustSyncUntilTestNightly : AbstractSyncTest() {

    private companion object : KLogging()

    val chainID: Long = 0L //In AbstractSyncTest, chainID is hard coded to 0L.
    override var mustSyncUntil = 1L //default value is -1
    val signers = 2
    val replicas = 0
    val blocksToSync = 2
    val syncNodeIndex = 0


    /**  Try to synchronize Node 0 to a height that does not exist.
     * Only 2 blocks are synced (to height 1). But Node 0 will not leave synchronizer until height 3 is reached.
     * Thus try block will time out.
     * In catch we check that we have not reached height 3, but only blocksToSynch-1 = 2-1 = 1
     */
    @Test
    @Disabled // This test was fixed in another branch, so ignore it here until merge
    fun testSyncUntilNonExistingHeight() {
        mustSyncUntil = 3L
        try {
            Awaitility.await().atMost(15, TimeUnit.SECONDS).until {
                runSyncTest(signers, replicas, setOf(syncNodeIndex), setOf(), blocksToSync)
                true
            }
        } catch (e: org.awaitility.core.ConditionTimeoutException) {
            val actual = nodes[syncNodeIndex].currentHeight(chainID)
            assertEquals((blocksToSync - 1).toLong(), actual)
        }
    }

    // Asserts when height mustSyncUntil is reached.
    @Test
    fun testSyncUntilHeight() {
        mustSyncUntil = 1L
        runSyncTest(signers, replicas, setOf(syncNodeIndex), setOf(), blocksToSync)
    }
}
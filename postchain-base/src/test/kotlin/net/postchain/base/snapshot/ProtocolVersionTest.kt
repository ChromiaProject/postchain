package net.postchain.base.snapshot

import net.postchain.base.runStorageCommand
import net.postchain.common.data.Hash
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.TreeMap

class ProtocolVersionTest : SnapshotBaseIT() {

    @OptIn(ExperimentalStdlibApi::class)
    private fun state(nonce: Byte) = nonce.toHexString().padEnd(64, 'f').hexStringToByteArray()

    // account state proof discrepancy between protocol versions 1 and 2
    // is verified in SnapshotIT.`test snapshot root hash, page root hash, and account state proofs`()

    @Test
    fun `verify account snapshot hash discrepancy between protocol versions 1 and 2`() {
        runStorageCommand(appConfig, hostChainId) { ctx ->
            // Init DB
            initBlockchain(chainID = hostChainId, ctx)
            val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)

            // Account states
            val states0 = TreeMap<Long, Hash>()
            val account5 = 5L // Account #5, belongs to the 2nd page
            states0[account5] = state(0)

            // Update snapshot at height0 for both protocol versions
            val height0 = 0L
            val stateRootHash0v1 = snapshot.updateSnapshot(height0, states0, protocolVersion = 1).toHex()
            val stateRootHash0v2 = snapshot.updateSnapshot(height0, states0, protocolVersion = 1).toHex()
            // Assert that both protocols generate equal hashes
            assertEquals(stateRootHash0v1, stateRootHash0v2)

            // Build a new block with no updates for both protocol versions
            val height1 = 1L
            val states1 = TreeMap<Long, Hash>()
            val stateRootHash1v1 = snapshot.updateSnapshot(height1, states1, protocolVersion = 1).toHex()
            val stateRootHash1v2 = snapshot.updateSnapshot(height1, states1, protocolVersion = 2).toHex()
            // Assert that state root hashes are NOT equal for different protocol versions
            assertNotEquals(stateRootHash1v1, stateRootHash1v2)
            // Assert that Protocol v2 returns state hash equal to ones calculated at height0 for both versions
            assertEquals(stateRootHash0v1, stateRootHash1v2)
            assertEquals(stateRootHash0v2, stateRootHash1v2)
        }
    }

}
package net.postchain.base.snapshot

import net.postchain.base.runStorageCommand
import net.postchain.common.data.Hash
import net.postchain.common.toHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.TreeMap

abstract class EmptyAccountStateTreeIT : SnapshotBaseIT() {

    @Test
    fun `empty account state tree`() {
        runStorageCommand(appConfig, hostChainId) { parentCtx ->
            // Init DB
            val (ctx, _) = initBlockchain(chainID = hostChainId, parentCtx)
            val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)

            // No states
            val states = TreeMap<Long, Hash>()

            // Update snapshot with empty states
            val stateRootHash = snapshot.updateSnapshot(0, states, protocolVersion).toHex()
            // Multiple updates are allowed
            val stateRootHash2 = snapshot.updateSnapshot(0, states, protocolVersion).toHex()
            assertEquals(stateRootHash, stateRootHash2)

            // Verify snapshot merkle root hash
            val expectedRootHash = calculateMerkleRoot(states).toHex()
            assertEquals(expectedRootHash, stateRootHash)
            assertEquals(expectedRootHash, stateRootHash2)
        }
    }
}
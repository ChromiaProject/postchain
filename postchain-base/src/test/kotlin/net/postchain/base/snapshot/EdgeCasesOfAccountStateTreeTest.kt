package net.postchain.base.snapshot

import net.postchain.base.runStorageCommand
import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.data.Hash
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.TreeMap

class EdgeCasesOfAccountStateTreeTest : SnapshotBaseIT() {

    /**
     * This test suite tests Protocol v2, by default.
     */
    override val protocolVersion: Int = 2

    @OptIn(ExperimentalStdlibApi::class)
    private fun state(nonce: Byte) = nonce.toHexString().padEnd(64, 'f').hexStringToByteArray()

    @Test
    fun `single account state, multiple equivalent updates in one block`() {
        runStorageCommand(appConfig, hostChainId) { parentCtx ->
            // Init DB
            val (ctx, _) = initBlockchain(chainID = hostChainId, parentCtx)
            val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)

            // Account states
            val states = TreeMap<Long, Hash>()
            states[0] = state(0)

            // Update snapshot
            val stateRootHash = snapshot.updateSnapshot(0, states, protocolVersion).toHex()
            // Multiple equivalent updates are allowed
            val stateRootHash2 = snapshot.updateSnapshot(0, states, protocolVersion).toHex()
            assertEquals(stateRootHash, stateRootHash2)

            // Verify snapshot merkle root hash
            val expectedRootHash = calculateMerkleRoot(states).toHex()
            assertEquals(expectedRootHash, stateRootHash)
            assertEquals(expectedRootHash, stateRootHash2)
        }
    }

    @Test
    fun `single account state, multiple different updates in one block`() {
        runStorageCommand(appConfig, hostChainId) { parentCtx ->
            // Init DB
            val (ctx, _) = initBlockchain(chainID = hostChainId, parentCtx)
            val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)

            // Account states
            val states = TreeMap<Long, Hash>()
            states[0] = state(0)

            // Update snapshot
            val stateRootHash = snapshot.updateSnapshot(0, states, protocolVersion).toHex()
            // Multiple non-equivalent updates are allowed
            states[0] = state(1)
            val stateRootHash2 = snapshot.updateSnapshot(0, states, protocolVersion).toHex()
            assertNotEquals(stateRootHash, stateRootHash2)

            // Verify snapshot merkle root hash
            val expectedRootHash = calculateMerkleRoot(states).toHex()
            assertEquals(expectedRootHash, stateRootHash2)
        }
    }

    @Test
    fun `two account states in same page, multiple different incremental updates in one block`() {
        runStorageCommand(appConfig, hostChainId) { parentCtx ->
            // Init DB
            val (ctx, _) = initBlockchain(chainID = hostChainId, parentCtx)
            val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)

            // Account states
            val states = TreeMap<Long, Hash>()
            states[0] = state(0)
            states[1] = state(1)

            // Update snapshot with states: [0, 1]
            val stateRootHash = snapshot.updateSnapshot(0, states, protocolVersion).toHex()
            // Account0: multiple non-equivalent updates are allowed
            states[0] = state(2) // [2, 1]
            val stateRootHash2 = snapshot.updateSnapshot(0, states, protocolVersion).toHex()
            assertNotEquals(stateRootHash, stateRootHash2)
            // Account1: multiple non-equivalent updates are allowed
            states[1] = state(3) // [2, 3]
            val incrementalUpdates = TreeMap(states.subMap(1, 2)) // [_, 3]
            val stateRootHash3 = snapshot.updateSnapshot(0, incrementalUpdates, protocolVersion).toHex()
            assertNotEquals(stateRootHash2, stateRootHash3)

            // Verify snapshot merkle root hash
            val expectedRootHash = calculateMerkleRoot(states).toHex()
            assertEquals(expectedRootHash, stateRootHash3)
        }
    }

    @Test
    fun `two account states in different pages, multiple different incremental updates in one block`() {
        runStorageCommand(appConfig, hostChainId) { parentCtx ->
            // Init DB
            val (ctx, _) = initBlockchain(chainID = hostChainId, parentCtx)
            val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)

            // Account states
            val states = TreeMap<Long, Hash>()
            states[0] = state(0)
            states[5] = state(1) // account belongs to the next page

            // Update snapshot with states: [[0, _, _, _], [_, 1, _, _]]
            val stateRootHash = snapshot.updateSnapshot(0, states, protocolVersion).toHex()
            // Account0: multiple non-equivalent updates are allowed
            states[0] = state(2) // [[2, _, _, _], [_, 1, _, )]]
            val stateRootHash2 = snapshot.updateSnapshot(0, states, protocolVersion).toHex()
            assertNotEquals(stateRootHash, stateRootHash2)
            // Account5: multiple non-equivalent updates are allowed
            states[5] = state(3) // [[2, _, _, _], [_, 3, _, )]]
            val incrementalUpdates = TreeMap(states.subMap(5, 6)) // [[_, _, _, _], [_, 3, _, _]]
            val stateRootHash3 = snapshot.updateSnapshot(0, incrementalUpdates, protocolVersion).toHex()
            assertNotEquals(stateRootHash2, stateRootHash3)

            // Verify snapshot merkle root hash
            val expectedRootHash = calculateMerkleRoot(states).toHex()
            assertEquals(expectedRootHash, stateRootHash3)
        }
    }

    @Test
    fun `calculate merkle proof for non-existing account in protocol v2`() {
        runStorageCommand(appConfig, hostChainId) { parentCtx ->
            // Init DB
            val (ctx, _) = initBlockchain(chainID = hostChainId, parentCtx)
            val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)

            // Account states
            val states = TreeMap<Long, Hash>()
            states[63] = state(0)

            // Update snapshot
            snapshot.updateSnapshot(0, states, 2).toHex()

            // Account 63
            val proofForAccount63 = snapshot.getMerkleProof(0, 63)
            assertArrayEquals(
                    Array(6) { EMPTY_HASH },
                    proofForAccount63.toTypedArray()
            )
            // Non-existing account
            // Note: Protocol v1 returns an empty proof (empty list) for a non-existing account
            val proofForNonExistingAccount = snapshot.getMerkleProof(0, 64)
            assertArrayEquals(
                    Array(6) { EMPTY_HASH },
                    proofForNonExistingAccount.toTypedArray()
            )
        }
    }
}
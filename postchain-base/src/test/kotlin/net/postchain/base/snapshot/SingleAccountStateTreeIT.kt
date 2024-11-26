package net.postchain.base.snapshot

import net.postchain.base.runStorageCommand
import net.postchain.common.data.Hash
import net.postchain.common.toHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.TreeMap

abstract class SingleAccountStateTreeIT : SnapshotBaseIT() {

    // Single account, single state update
    @Test
    fun `single account with single state update`() {
        runStorageCommand(appConfig, hostChainId) { parentCtx ->
            accounts.forEach { a ->
                val account = a.toLong()
                logger.info { "Verify snapshot for account $account" }

                // Init DB
                val (ctx, db) = initBlockchain(chainID = account, parentCtx)
                val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)

                // Build states
                val states = TreeMap<Long, Hash>()
                states[account] = buildAccountState(account)

                // Insert state, update snapshot
                db.insertState(ctx, PREFIX, 0, account, states[account]!!)
                val stateRootHash = snapshot.updateSnapshot(0, states, protocolVersion).toHex()

                // Verify snapshot merkle root hash
                val expectedRootHash = calculateMerkleRoot(states).toHex()
                assertEquals(expectedRootHash, stateRootHash)

                // Verify account state proof
                verifyAccountStateProof(expectedRootHash, account, states[account]!!, 0, snapshot)
            }
        }
    }

    // Single account, multiple state updates
    @ParameterizedTest
    @CsvSource(
            "0, 10",     // no updates, only initial state
            "1, 10",     // 1 update
            "2, 10",     // 2 updates in one block
            "32, 10",    // 32 updates in one block
    )
    fun `single account with multiple state updates in one block`(nrOfUpdates: Int, height: Long) {
        logger.info { "single account with $nrOfUpdates state updates in one block" }

        runStorageCommand(appConfig, hostChainId) { parentCtx ->
            accounts.forEach { a ->
                val account = a.toLong()
                logger.info { "Verify snapshot for account $account" }

                // Init DB
                val (ctx, db) = initBlockchain(chainID = account, parentCtx)
                val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)

                // Build states
                val states = TreeMap<Long, Hash>()
                states[account] = buildAccountState(account, 0)
                db.insertState(ctx, PREFIX, height, account, states[account]!!)

                // Updates
                repeat(nrOfUpdates) { u ->
                    // Build state update
                    val oldState = states[account]!!
                    states[account] = buildAccountState(account, u + 1)
                    assertNotEquals(oldState.toHex(), states[account]!!.toHex())

                    // Insert state update
                    db.insertState(ctx, PREFIX, height, account, states[account]!!)
                }

                // Verify snapshot merkle root hash
                val stateRootHash = snapshot.updateSnapshot(height, states, protocolVersion).toHex()
                val expectedRootHash = calculateMerkleRoot(states).toHex()
                assertEquals(expectedRootHash, stateRootHash)

                // Verify account state proof
                verifyAccountStateProof(expectedRootHash, account, states[account]!!, height, snapshot)
            }
        }
    }

    @ParameterizedTest
    @CsvSource(
            "0, 10, 1",     // no updates, only initial state
            "2, 10, 1",     // 2 updates in sequential blocks
            "2, 10, 20",    // 2 updates in two blocks with gap between them
            "32, 10, 1",    // 32 updates in sequential blocks
            "32, 10, 20",   // 32 updates in two blocks with gap between them
    )
    fun `single account with multiple state updates in different blocks`(nrOfUpdates: Int, height: Long, heightStep: Int) {
        require(heightStep > 0) { "height step must be greater than zero" }

        logger.info {
            "single account with $nrOfUpdates state updates in " + when (heightStep) {
                1 -> "sequential blocks"
                else -> "blocks with gap between them"
            }
        }

        runStorageCommand(appConfig, hostChainId) { parentCtx ->
            accounts.forEach { a ->
                val account = a.toLong()
                logger.info { "Verify snapshot for account $account" }

                // Init DB
                val (ctx, db) = initBlockchain(chainID = account, parentCtx)
                val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)

                // Build states
                var currentHeight = height
                val states = TreeMap<Long, Hash>()
                states[account] = buildAccountState(account, 0)
                db.insertState(ctx, PREFIX, currentHeight, account, states[account]!!)
                var stateRootHash = snapshot.updateSnapshot(currentHeight, states, protocolVersion).toHex()

                // Updates
                repeat(nrOfUpdates) { u ->
                    currentHeight += heightStep

                    // Build state update
                    val oldState = states[account]!!
                    states[account] = buildAccountState(account, u + 1)
                    assertNotEquals(oldState.toHex(), states[account]!!.toHex())

                    // Insert state update
                    db.insertState(ctx, PREFIX, currentHeight, account, states[account]!!)
                    val stateRootHashNew = snapshot.updateSnapshot(currentHeight, states, protocolVersion).toHex()

                    // Verify that state has changed
                    assertNotEquals(stateRootHash, stateRootHashNew)

                    // Verify account state proof
                    verifyAccountStateProof(stateRootHashNew, account, states[account]!!, currentHeight, snapshot)

                    // Prepare next iteration
                    stateRootHash = stateRootHashNew
                }

                // Verify snapshot merkle root hash
                val expectedRootHash = calculateMerkleRoot(states).toHex()
                assertEquals(expectedRootHash, stateRootHash)

                // Verify account state proof
                verifyAccountStateProof(expectedRootHash, account, states[account]!!, currentHeight, snapshot)
            }
        }
    }
}
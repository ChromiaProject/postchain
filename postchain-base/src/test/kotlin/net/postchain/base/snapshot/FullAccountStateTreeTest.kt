package net.postchain.base.snapshot

import net.postchain.base.runStorageCommand
import net.postchain.common.data.Hash
import net.postchain.common.toHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.TreeMap

abstract class FullAccountStateTreeTest : SnapshotBaseIT() {

    // Full tree with 64 accounts, single state update each, same page
    @ParameterizedTest
    @CsvSource(
            "0, 10",      // no updates, only initial state
            "1, 10",      // 1 update
            "2, 10",      // 2 updates in one block
            "32, 10",     // 32 updates in one block
    )
    fun `full tree with 64 accounts with single state update each in one block`(nrOfUpdates: Int, height: Long) {
        logger.info { "full tree with 64 accounts with $nrOfUpdates state updates each in one block" }

        runStorageCommand(appConfig, hostChainId) { parentCtx ->
            logger.info { "Verify snapshot for all accounts" }

            // Init DB
            val (ctx, db) = initBlockchain(chainID = hostChainId, parentCtx)
            val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)

            // Build states for account1
            val states = TreeMap<Long, Hash>()
            accounts.forEach { a ->
                val account = a.toLong()
                states[account] = buildAccountState(account, 0)
                db.insertState(ctx, PREFIX, height, account, states[account]!!)
            }

            // Updates
            repeat(nrOfUpdates) { u ->
                accounts.forEach { a ->
                    // Update account state
                    val account = a.toLong()
                    val oldState = states[account]!!
                    states[account] = buildAccountState(account, u + 1)
                    assertNotEquals(oldState.toHex(), states[account]!!.toHex())

                    // Insert state updates
                    db.insertState(ctx, PREFIX, height, account, states[account]!!)
                }
            }

            // Verify snapshot merkle root hash
            val stateRootHash = snapshot.updateSnapshot(height, states, protocolVersion).toHex()
            val expectedRootHash = calculateMerkleRoot(states).toHex()
            assertEquals(expectedRootHash, stateRootHash)

            // Verify account state proof
            accounts.forEach { a ->
                val account = a.toLong()
                verifyAccountStateProof(expectedRootHash, account, states[account]!!, height, snapshot)
            }
        }
    }

    @ParameterizedTest
    @CsvSource(
            "0, 10, 1",      // no updates, only initial state

            "2, 10, 1",      // 2 updates in sequential blocks
            "2, 10, 10",     // 2 updates in two blocks with gap between them

            "32, 10, 1",     // 32 updates in sequential blocks
            "32, 10, 20",    // 32 updates in two blocks with gap between them
    )
    fun `full tree with 64 accounts with single state update each in different blocks`(nrOfUpdates: Int, height: Long, heightStep: Int) {
        require(heightStep > 0) { "height step must be greater than zero" }

        logger.info {
            "full tree with 64 accounts with $nrOfUpdates state updates each in " + when (heightStep) {
                1 -> "sequential blocks"
                else -> "blocks with gap between them"
            }
        }

        runStorageCommand(appConfig, hostChainId) { parentCtx ->
            logger.info { "Verify snapshot for all accounts" }

            // Init DB
            val (ctx, db) = initBlockchain(chainID = hostChainId, parentCtx)
            val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)

            // Build states for account1
            var currentHeight = height
            val states = TreeMap<Long, Hash>()
            accounts.forEach { a ->
                val account = a.toLong()
                states[account] = buildAccountState(account, 0)
                db.insertState(ctx, PREFIX, currentHeight, account, states[account]!!)
            }
            var stateRootHash = snapshot.updateSnapshot(currentHeight, states, protocolVersion).toHex()

            // Updates
            repeat(nrOfUpdates) { u ->
                currentHeight += heightStep
                accounts.forEach { a ->
                    // Update account state
                    val account = a.toLong()
                    val oldState = states[account]!!
                    states[account] = buildAccountState(account, u + 1)
                    assertNotEquals(oldState.toHex(), states[account]!!.toHex())

                    // Insert state updates
                    db.insertState(ctx, PREFIX, currentHeight, account, states[account]!!)
                }

                // Verify that state has changed
                val stateRootHashNew = snapshot.updateSnapshot(currentHeight, states, protocolVersion).toHex()
                assertNotEquals(stateRootHash, stateRootHashNew)

                // Verify account state proof
                accounts.forEach { a ->
                    val account = a.toLong()
                    verifyAccountStateProof(stateRootHashNew, account, states[account]!!, currentHeight, snapshot)
                }

                // Prepare next iteration
                stateRootHash = stateRootHashNew
            }

            // Verify snapshot merkle root hash
            val expectedRootHash = calculateMerkleRoot(states).toHex()
            assertEquals(expectedRootHash, stateRootHash)

            // Verify account state proof
            accounts.forEach { a ->
                val account = a.toLong()
                verifyAccountStateProof(expectedRootHash, account, states[account]!!, currentHeight, snapshot)
            }
        }
    }
}
package net.postchain.base.snapshot

import net.postchain.base.runStorageCommand
import net.postchain.common.data.Hash
import net.postchain.common.toHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.TreeMap

abstract class TwoAccountsStateTreeIT : SnapshotBaseIT() {

    // Two accounts, same page, multiple state update
    @ParameterizedTest
    @CsvSource(
            "0, 10",     // no updates, only initial state
            "1, 10",     // 1 update
            "2, 10",     // 2 updates in one block
            "32, 10",    // 32 updates in one block
    )
    fun `two accounts on same page with multiple state updates each in one block`(nrOfUpdates: Int, height: Long) {
        logger.info { "two accounts on same page with $nrOfUpdates state updates each in one block" }

        runStorageCommand(appConfig, hostChainId) { parentCtx ->
            for (account in accounts.indices step pageSize) {
                val account1 = accounts[account].toLong()
                val account2 = accounts[account + 1].toLong()
                logger.info { "Verify snapshot for accounts: $account1, $account2" }

                // Init DB
                val (ctx, db) = initBlockchain(chainID = account1, parentCtx)
                val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)

                // Build states for account1
                val states = TreeMap<Long, Hash>()
                states[account1] = buildAccountState(account1, 0)
                db.insertState(ctx, PREFIX, height, account1, states[account1]!!)
                // Build states for account2
                states[account2] = buildAccountState(account2, 0)
                db.insertState(ctx, PREFIX, height, account2, states[account2]!!)

                // Updates
                repeat(nrOfUpdates) { u ->
                    // Build state update for account1
                    val oldState1 = states[account1]!!
                    states[account1] = buildAccountState(account1, u + 1)
                    assertNotEquals(oldState1.toHex(), states[account1]!!.toHex())

                    // Build state update for account2
                    val oldState2 = states[account2]!!
                    states[account2] = buildAccountState(account2, u + 1)
                    assertNotEquals(oldState2.toHex(), states[account2]!!.toHex())

                    // Insert state updates
                    db.insertState(ctx, PREFIX, height, account1, states[account1]!!)
                    db.insertState(ctx, PREFIX, height, account2, states[account2]!!)
                }

                // Verify snapshot merkle root hash
                val stateRootHash = snapshot.updateSnapshot(height, states, protocolVersion).toHex()
                val expectedRootHash = calculateMerkleRoot(states).toHex()
                assertEquals(expectedRootHash, stateRootHash)

                // Verify account state proof
                verifyAccountStateProof(expectedRootHash, account1, states[account1]!!, height, snapshot)
                verifyAccountStateProof(expectedRootHash, account2, states[account2]!!, height, snapshot)
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
    fun `two accounts on same page with multiple state updates each in different blocks`(nrOfUpdates: Int, height: Long, heightStep: Int) {
        require(heightStep > 0) { "height step must be greater than zero" }

        logger.info {
            "two accounts on same page with $nrOfUpdates state updates each in " + when (heightStep) {
                1 -> "sequential blocks"
                else -> "blocks with gap between them"
            }
        }

        runStorageCommand(appConfig, hostChainId) { parentCtx ->
            for (account in accounts.indices step pageSize) {
                val account1 = accounts[account].toLong()
                val account2 = accounts[account + 1].toLong()
                logger.info { "Verify snapshot for accounts: $account1, $account2" }

                // Init DB
                val (ctx, db) = initBlockchain(chainID = account1, parentCtx)
                val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)

                // Build states for account1
                var currentHeight = height
                val states = TreeMap<Long, Hash>()
                states[account1] = buildAccountState(account1, 0)
                db.insertState(ctx, PREFIX, currentHeight, account1, states[account1]!!)
                // Build states for account2
                states[account2] = buildAccountState(account2, 0)
                db.insertState(ctx, PREFIX, currentHeight, account2, states[account2]!!)

                // Update snapshot
                var stateRootHash = snapshot.updateSnapshot(currentHeight, states, protocolVersion).toHex()

                // Updates
                repeat(nrOfUpdates) { u ->
                    currentHeight += heightStep

                    // Build state update for account1
                    val oldState1 = states[account1]!!
                    states[account1] = buildAccountState(account1, u + 1)
                    assertNotEquals(oldState1.toHex(), states[account1]!!.toHex())

                    // Build state update for account2
                    val oldState2 = states[account2]!!
                    states[account2] = buildAccountState(account2, u + 1)
                    assertNotEquals(oldState2.toHex(), states[account2]!!.toHex())

                    // Insert state updates
                    db.insertState(ctx, PREFIX, currentHeight, account1, states[account1]!!)
                    db.insertState(ctx, PREFIX, currentHeight, account2, states[account2]!!)
                    val stateRootHashNew = snapshot.updateSnapshot(currentHeight, states, protocolVersion).toHex()

                    // Verify that state has changed
                    assertNotEquals(stateRootHash, stateRootHashNew)

                    // Verify account state proof
                    verifyAccountStateProof(stateRootHashNew, account1, states[account1]!!, currentHeight, snapshot)
                    verifyAccountStateProof(stateRootHashNew, account2, states[account2]!!, currentHeight, snapshot)

                    // Prepare next iteration
                    stateRootHash = stateRootHashNew
                }

                // Verify snapshot merkle root hash
                val expectedRootHash = calculateMerkleRoot(states).toHex()
                assertEquals(expectedRootHash, stateRootHash)

                // Verify account state proof
                verifyAccountStateProof(expectedRootHash, account1, states[account1]!!, currentHeight, snapshot)
                verifyAccountStateProof(expectedRootHash, account2, states[account2]!!, currentHeight, snapshot)
            }
        }
    }

    // Two accounts, different pages, multiple state updates
    @ParameterizedTest
    @CsvSource(
            "0, 10",     // no updates, only initial state
            "1, 10",     // 1 update
            "2, 10",     // 2 updates in one block
            "32, 10",    // 32 updates in one block
    )
    fun `two accounts on different pages with multiple state updates in one block`(nrOfUpdates: Int, height: Long) {
        logger.info { "two accounts on different pages with $nrOfUpdates state updates each in one block" }

        runStorageCommand(appConfig, hostChainId) { parentCtx ->
            for (account in accounts.indices step (pageSize * 2)) {
                val account1 = accounts[account].toLong()
                val account2 = accounts[account + pageSize].toLong() // account2 belongs to the next page
                logger.info { "Verify snapshot for accounts: $account1, $account2" }

                // Init DB
                val (ctx, db) = initBlockchain(chainID = account1, parentCtx)
                val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)

                val states = TreeMap<Long, Hash>()

                // Build states for account1
                states[account1] = buildAccountState(account1, 0)
                db.insertState(ctx, PREFIX, height, account1, states[account1]!!)
                // Build states for account2
                states[account2] = buildAccountState(account2, 0)
                db.insertState(ctx, PREFIX, height, account2, states[account2]!!)

                // Updates
                repeat(nrOfUpdates) { u ->
                    // Build state update for account1
                    val oldState1 = states[account1]!!
                    states[account1] = buildAccountState(account1, u + 1)
                    assertNotEquals(oldState1.toHex(), states[account1]!!.toHex())

                    // Build state update for account2
                    val oldState2 = states[account2]!!
                    states[account2] = buildAccountState(account2, u + 1)
                    assertNotEquals(oldState2.toHex(), states[account2]!!.toHex())

                    // Insert state updates
                    db.insertState(ctx, PREFIX, height, account1, states[account1]!!)
                    db.insertState(ctx, PREFIX, height, account2, states[account2]!!)
                }

                // Verify snapshot merkle root hash
                val stateRootHash = snapshot.updateSnapshot(height, states, protocolVersion).toHex()
                val expectedRootHash = calculateMerkleRoot(states).toHex()
                assertEquals(expectedRootHash, stateRootHash)

                // Verify account state proof
                verifyAccountStateProof(expectedRootHash, account1, states[account1]!!, height, snapshot)
                verifyAccountStateProof(expectedRootHash, account2, states[account2]!!, height, snapshot)
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
    fun `two accounts on different pages with multiple state updates each in different blocks`(nrOfUpdates: Int, height: Long, heightStep: Int) {
        require(heightStep > 0) { "height step must be greater than zero" }

        logger.info {
            "two accounts on different pages with $nrOfUpdates state updates each in " + when (heightStep) {
                1 -> "sequential blocks"
                else -> "blocks with gap between them"
            }
        }

        runStorageCommand(appConfig, hostChainId) { parentCtx ->
            for (account in accounts.indices step (pageSize * 2)) {
                val account1 = accounts[account].toLong()
                val account2 = accounts[account + pageSize].toLong() // account2 belongs to the next page
                logger.info { "Verify snapshot for accounts: $account1, $account2" }

                // Init DB
                val (ctx, db) = initBlockchain(chainID = account1, parentCtx)
                val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)

                var currentHeight = height
                val states = TreeMap<Long, Hash>()

                // Build states for account1
                states[account1] = buildAccountState(account1, 0)
                db.insertState(ctx, PREFIX, currentHeight, account1, states[account1]!!)
                // Build states for account2
                states[account2] = buildAccountState(account2, 0)
                db.insertState(ctx, PREFIX, currentHeight, account2, states[account2]!!)

                // Update snapshot
                var stateRootHash = snapshot.updateSnapshot(currentHeight, states, protocolVersion).toHex()

                // Updates
                repeat(nrOfUpdates) { u ->
                    currentHeight += heightStep

                    // Build state update for account1
                    val oldState1 = states[account1]!!
                    states[account1] = buildAccountState(account1, u + 1)
                    assertNotEquals(oldState1.toHex(), states[account1]!!.toHex())

                    // Build state update for account2
                    val oldState2 = states[account2]!!
                    states[account2] = buildAccountState(account2, u + 1)
                    assertNotEquals(oldState2.toHex(), states[account2]!!.toHex())

                    // Insert state updates
                    db.insertState(ctx, PREFIX, currentHeight, account1, states[account1]!!)
                    db.insertState(ctx, PREFIX, currentHeight, account2, states[account2]!!)
                    val stateRootHashNew = snapshot.updateSnapshot(currentHeight, states, protocolVersion).toHex()

                    // Verify that state has changed
                    assertNotEquals(stateRootHash, stateRootHashNew)

                    // Verify account state proof
                    verifyAccountStateProof(stateRootHashNew, account1, states[account1]!!, currentHeight, snapshot)
                    verifyAccountStateProof(stateRootHashNew, account2, states[account2]!!, currentHeight, snapshot)

                    // Prepare next iteration
                    stateRootHash = stateRootHashNew
                }

                // Verify snapshot merkle root hash
                val expectedRootHash = calculateMerkleRoot(states).toHex()
                assertEquals(expectedRootHash, stateRootHash)

                // Verify account state proof
                verifyAccountStateProof(expectedRootHash, account1, states[account1]!!, currentHeight, snapshot)
                verifyAccountStateProof(expectedRootHash, account2, states[account2]!!, currentHeight, snapshot)
            }
        }
    }
}
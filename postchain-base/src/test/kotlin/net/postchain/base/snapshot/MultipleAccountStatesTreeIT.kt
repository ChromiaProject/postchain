package net.postchain.base.snapshot

import net.postchain.base.runStorageCommand
import net.postchain.common.data.Hash
import net.postchain.common.toHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.TreeMap

abstract class MultipleAccountStatesTreeIT : SnapshotBaseIT() {

    // 16 accounts, with multiple state updates
    @ParameterizedTest
    @CsvSource(
            "1, 0, 10",      // sequential accounts, no updates, only initial state
            "2, 0, 10",      // every other account, no updates, only initial state
            "3, 0, 10",      // every third account, no updates, only initial state

            "1, 1, 10",      // sequential accounts, 1 update
            "2, 1, 10",      // every other account, 1 update
            "3, 1, 10",      // every third account, 1 update

            "1, 2, 10",      // sequential accounts, 2 updates in one block

            "1, 32, 10",     // sequential accounts, 32 updates in one block
    )
    fun `16 accounts with multiple state updates each in one block`(accountStep: Int, nrOfUpdates: Int, height: Long) {
        logger.info { "16 accounts with $nrOfUpdates state updates each in one block" }

        runStorageCommand(appConfig, hostChainId) { parentCtx ->
            // Select accounts to test
            val accountIds = accounts.slice(accounts.indices step accountStep).take(16)
            logger.info { "Verify snapshot for accounts: ${accountIds.toTypedArray().contentToString()}" }

            // Init DB
            val (ctx, db) = initBlockchain(chainID = hostChainId, parentCtx)
            val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)

            // Build states for account1
            val states = TreeMap<Long, Hash>()
            accountIds.indices.forEach { i ->
                val account = i.toLong()
                states[account] = buildAccountState(account, 0)
                db.insertState(ctx, PREFIX, height, account, states[account]!!)
            }

            // Updates
            repeat(nrOfUpdates) { u ->
                accountIds.indices.forEach { i ->
                    // Update account state
                    val account = i.toLong()
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
            accountIds.indices.forEach { i ->
                val account = i.toLong()
                verifyAccountStateProof(expectedRootHash, account, states[account]!!, height, snapshot)
            }
        }
    }

    @ParameterizedTest
    @CsvSource(
            "1, 0, 10, 1",      // sequential accounts, no updates, only initial state
            "2, 0, 10, 1",      // every other account, no updates, only initial state
            "3, 0, 10, 1",      // every third account, no updates, only initial state

            "2, 2, 10, 1",      // every other account, 2 updates in sequential blocks
            "3, 2, 10, 10",     // every third account, 2 updates in two blocks with gap between them

            "2, 32, 10, 1",     // every other account, 32 updates in sequential blocks
            "3, 32, 10, 20",    // every third account, 32 updates in two blocks with gap between them
    )
    fun `16 accounts with multiple state updates each in different blocks`(accountStep: Int, nrOfUpdates: Int, height: Long, heightStep: Int) {
        require(heightStep > 0) { "height step must be greater than zero" }

        logger.info {
            "16 accounts with $nrOfUpdates state updates each in " + when (heightStep) {
                1 -> "sequential blocks"
                else -> "blocks with gap between them"
            }
        }

        runStorageCommand(appConfig, hostChainId) { parentCtx ->
            // Select accounts to test
            val accountIds = accounts.slice(accounts.indices step accountStep).take(16)
            logger.info { "Verify snapshot for accounts: ${accountIds.toTypedArray().contentToString()}" }

            // Init DB
            val (ctx, db) = initBlockchain(chainID = hostChainId, parentCtx)
            val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)

            // Build states for account1
            var currentHeight = height
            val states = TreeMap<Long, Hash>()
            accountIds.indices.forEach { i ->
                val account = i.toLong()
                states[account] = buildAccountState(account, 0)
                db.insertState(ctx, PREFIX, currentHeight, account, states[account]!!)
            }
            var stateRootHash = snapshot.updateSnapshot(currentHeight, states, protocolVersion).toHex()

            // Updates
            repeat(nrOfUpdates) { u ->
                currentHeight += heightStep

                accountIds.indices.forEach { i ->
                    // Update account state
                    val account = i.toLong()
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
                accountIds.indices.forEach { i ->
                    val account = i.toLong()
                    verifyAccountStateProof(stateRootHashNew, account, states[account]!!, currentHeight, snapshot)
                }

                // Prepare next iteration
                stateRootHash = stateRootHashNew
            }

            // Verify snapshot merkle root hash
            val expectedRootHash = calculateMerkleRoot(states).toHex()
            assertEquals(expectedRootHash, stateRootHash)

            // Verify account state proof
            accountIds.indices.forEach { i ->
                val account = i.toLong()
                verifyAccountStateProof(expectedRootHash, account, states[account]!!, currentHeight, snapshot)
            }
        }
    }
}
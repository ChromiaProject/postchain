package net.postchain.base.snapshot

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.runStorageCommand
import net.postchain.common.BlockchainRid
import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.data.Hash
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.wrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.TreeMap
import kotlin.random.Random

class SnapshotTest : SnapshotBaseIT() {

    @ParameterizedTest
    @ValueSource(ints = [1, 2])
    fun `test snapshot root hash, page root hash, and account state proofs`(protocolVer: Int) {
        runStorageCommand(appConfig, 0L) { ctx ->
            val db = DatabaseAccess.of(ctx).apply {
                initializeBlockchain(ctx, BlockchainRid.ZERO_RID)
                createPageTable(ctx, "${PREFIX}_snapshot")
                createStateLeafTable(ctx, PREFIX)
                createStateLeafTableIndex(ctx, PREFIX, 0)
            }
            val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)
            val states = TreeMap<Long, Hash>()

            // Height 1, 10 accounts
            val height1 = 1L
            states[0] = "044852b2a670ade5407e78fb2863c51de9fcb96542a07186fe3aeda6bb8a116d".hexStringToByteArray()
            states[1] = "c89efdaa54c0f20c7adf612882df0950f5a951637e0307cdcb4c672f298b8bc6".hexStringToByteArray()
            states[2] = EMPTY_HASH
            states[3] = "2a80e1ef1d7842f27f2e6be0972bb708b9a135c38860dbe73c27c3486c34f4de".hexStringToByteArray()
            states[4] = "13600b294191fc92924bb3ce4b969c1e7e2bab8f4c93c3fc6d0a51733df3c060".hexStringToByteArray()
            states[5] = "ceebf77a833b30520287ddd9478ff51abbdffa30aa90a8d655dba0e8a79ce0c1".hexStringToByteArray()
            states[6] = "e455bf8ea6e7463a1046a0b52804526e119b4bf5136279614e0b1e8e296a4e2d".hexStringToByteArray()
            states[7] = "52f1a9b320cab38e5da8a8f97989383aab0a49165fc91c737310e4f7e9821021".hexStringToByteArray()
            states[8] = "e4b1702d9298fee62dfeccc57d322a463ad55ca201256d01f62b45b2e1c21c10".hexStringToByteArray()
            states[9] = "d2f8f61201b2b11a78d6e866abc9c3db2ae8631fa656bfe5cb53668255367afb".hexStringToByteArray()
            states.forEach { (n, data) -> db.insertState(ctx, PREFIX, height1, n, data) }
            // Verify initial states
            states.forEach { (n, data) ->
                val accountState = db.getState(ctx, PREFIX, height1, n)
                assertNotNull(accountState)
                assertTrue(accountState!!.data.contentEquals(data))
            }
            // Verify snapshot root hash
            val stateRootHash1 = snapshot.updateSnapshot(height1, states, protocolVer)
            snapshot.pruneSnapshot(height1)
            val expectedRootHash1 = calculateMerkleRoot(states)
            assertEquals(expectedRootHash1.toHex(), stateRootHash1.toHex())

            // Verify page0's hash
            val page = snapshot.readPage(1, 2, 0)
            assertEquals(
                    Node(states.values.toList().subList(0, 4)).hash().toHex(),
                    page!!.childHashes[0].toHex()
            )


            // Height2, some accounts get updates
            val height2 = height1 + 1
            val states2 = TreeMap<Long, Hash>()
            states2[2] = "ad7c5bef027816a800da1736444fb58a807ef4c9603b7848673f7e3a68eb14a5".hexStringToByteArray()
            states2[8] = "9ae6066ff8547d3138cce35b150f93047df88fa376c8808f462d3bbdbcb4a690".hexStringToByteArray()
            states2[9] = "c41c8390c0da0418b7667ee0df6c246c26c4be6c0618368dce5a916e8008b0db".hexStringToByteArray()
            states.putAll(states2)
            states2.forEach { (n, data) -> db.insertState(ctx, PREFIX, height2, n, data) }
            // Verify updates
            states2.forEach { (n, data) ->
                val accountState = db.getState(ctx, PREFIX, height2, n)
                assertNotNull(accountState)
                assertTrue(accountState!!.data.contentEquals(data))
            }
            // Verify snapshot root hash
            val stateRootHash2 = snapshot.updateSnapshot(height2, states2, protocolVer)
            snapshot.pruneSnapshot(height2)
            val expectedRootHash2 = calculateMerkleRoot(states)
            assertEquals(expectedRootHash2.toHex(), stateRootHash2.toHex())

            // Verify the highest level page
            assertEquals(2, snapshot.highestLevelPage(height2))


            // Height3, some accounts get updates
            val height3 = height2 + 1
            val states3 = TreeMap<Long, Hash>()
            states3[10] = "1a192fabce13988b84994d4296e6cdc418d55e2f1d7f942188d4040b94fc57ac".hexStringToByteArray()
            states3[11] = "7880aec93413f117ef14bd4e6d130875ab2c7d7d55a064fac3c2f7bd51516380".hexStringToByteArray()
            states.putAll(states3)
            states3.forEach { (n, data) -> db.insertState(ctx, PREFIX, height3, n, data) }
            // Verify updates
            states3.forEach { (n, data) ->
                val accountState = db.getState(ctx, PREFIX, height3, n)
                assertNotNull(accountState)
                assertTrue(accountState!!.data.contentEquals(data))
            }
            snapshot.updateSnapshot(height3, states3, protocolVer)
            snapshot.pruneSnapshot(height3)
            // Verify the highest level page
            assertEquals(2, snapshot.highestLevelPage(height3))


            // Height4, some accounts get updates, new accounts are being added
            val height4 = height3 + 1
            val states4 = TreeMap<Long, Hash>()
            states4[0] = "8c18210df0d9514f2d2e5d8ca7c100978219ee80d3968ad850ab5ead208287b3".hexStringToByteArray()
            states4[12] = "7f8b6b088b6d74c2852fc86c796dca07b44eed6fb3daf5e6b59f7c364db14528".hexStringToByteArray()
            states4[13] = "789bcdf275fa270780a52ae3b79bb1ce0fda7e0aaad87b57b74bb99ac290714a".hexStringToByteArray()
            states4[14] = "5c4c6aa067b6f8e6cb38e6ab843832a94d1712d661a04d73c517d6a1931a9e5d".hexStringToByteArray()
            states4[15] = "1d3be50b2bb17407dd170f1d5da128d1def30c6b1598d6a629e79b4775265526".hexStringToByteArray()
            states.putAll(states4)
            states4.forEach { (n, data) -> db.insertState(ctx, PREFIX, height4, n, data) }
            // Verify updates
            states4.forEach { (n, data) ->
                val accountState = db.getState(ctx, PREFIX, height4, n)
                assertNotNull(accountState)
                assertTrue(accountState!!.data.contentEquals(data))
            }
            // Verify snapshot root hash
            val stateRootHash4 = snapshot.updateSnapshot(height4, states4, protocolVer)
            snapshot.pruneSnapshot(height4)
            val expectedRootHash4 = calculateMerkleRoot(states)
            assertEquals(expectedRootHash4.toHex(), stateRootHash4.toHex())
            // Verify the highest level page
            assertEquals(2, snapshot.highestLevelPage(height4))


            // Height5, add account16
            val height5 = height4 + 1
            val states5 = TreeMap<Long, Hash>()
            states5[16] = "277ab82e5a4641341820a4a2933a62c1de997e42e92548657ae21b3728d580fe".hexStringToByteArray()
            states.putAll(states5)
            states5.forEach { (n, data) -> db.insertState(ctx, PREFIX, height5, n, data) }
            // Verify updates
            states5.forEach { (n, data) ->
                val accountState = db.getState(ctx, PREFIX, height5, n)
                assertNotNull(accountState)
                assertTrue(accountState!!.data.contentEquals(data))
            }
            // Verify snapshot root hash
            val stateRootHash5 = snapshot.updateSnapshot(height5, states5, protocolVer)
            snapshot.pruneSnapshot(height5)
            assertEquals(4, snapshot.highestLevelPage(height5))
            assertEquals(4, snapshot.highestLevelPage(height5 + 100))
            val expectedRootHash5 = calculateMerkleRoot(states)
            assertEquals(expectedRootHash5.toHex(), stateRootHash5.toHex())

            // Verify pages
            // - page0
            assertNotNull(snapshot.readPage(height5, 2, 0))
            assertNotNull(snapshot.readPage(height5 - 1, 2, 0))
            assertNull(snapshot.readPage(height5 - snapshotsToKeep, 2, 0))

            // Verify merkle proofs for account0 at heights 3, 4, 5
            // - height3
            val account0 = 0L
            val proofAtHeight3 = snapshot.getMerkleProof(height5 - snapshotsToKeep, account0)
            assertEquals(2, proofAtHeight3.size) // Note: Protocol v1 returns an empty proof
            // - height4
            val proofAtHeight4 = snapshot.getMerkleProof(height5 - 1, account0)
            val merkleRoot4 = calculateMerkleRoot(proofAtHeight4, account0, states[account0]!!)
            assertEquals(stateRootHash4.toHex(), merkleRoot4.toHex())
            // - height5
            val proofAtHeight5 = snapshot.getMerkleProof(height5, account0)
            val merkleRoot5 = calculateMerkleRoot(proofAtHeight5, account0, states[account0]!!)
            assertEquals(stateRootHash5.toHex(), merkleRoot5.toHex())

            // Verify merkle proofs for account2 at heights 3, 4, 5
            // - height3
            val account2 = 2L
            val account2ProofAtHeight3 = snapshot.getMerkleProof(height5 - snapshotsToKeep, account2)
            assertEquals(2, account2ProofAtHeight3.size) // Note: Protocol v1 returns an empty proof
            // - height4
            val account2ProofAtHeight4 = snapshot.getMerkleProof(height5 - 1, account2)
            val account2MerkleRoot4 = calculateMerkleRoot(account2ProofAtHeight4, account2, states[account2]!!)
            assertEquals(stateRootHash4.toHex(), account2MerkleRoot4.toHex())
            // - height5
            val account2ProofAtHeight5 = snapshot.getMerkleProof(height5, account2)
            val account2MerkleRoot5 = calculateMerkleRoot(account2ProofAtHeight5, account2, states[account2]!!)
            assertEquals(stateRootHash5.toHex(), account2MerkleRoot5.toHex())

            // Verify merkle proofs for account16 at heights 4, 5
            val account16 = 16L
            val account16ProofAtHeight4 = snapshot.getMerkleProof(height5 - 1, account16)
            assertEquals(4, account16ProofAtHeight4.size) // Note: Protocol v1 returns an empty proof

            val account16ProofAtHeight5 = snapshot.getMerkleProof(height5, account16)
            val account16MerkleRoot5 = calculateMerkleRoot(account16ProofAtHeight5, account16, states5[account16]!!)
            assertEquals(stateRootHash5.toHex(), account16MerkleRoot5.toHex())
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [7, 35])
    fun testRandomSizeSnapshots(maxSize: Int) {
        val random = Random(1234) // Fixed seed for reproducibility
        val prefix2 = "prefix2"
        val protocolV2 = 2
        val rangeProofVerifier = VerifyRangeProof(ds)

        runStorageCommand(appConfig, 0L) { ctx ->
            val db = DatabaseAccess.of(ctx).apply {
                initializeBlockchain(ctx, BlockchainRid.ZERO_RID)
                createPageTable(ctx, "${PREFIX}_snapshot")
                createStateLeafTable(ctx, PREFIX)
                createStateLeafTableIndex(ctx, PREFIX, 0)

                createPageTable(ctx, "${prefix2}_snapshot")
            }

            val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)
            val snapshotT = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, prefix2)


            var stateRootHash = EMPTY_HASH
            val states = TreeMap<Long, Hash>()
            var blockHeight = 1L

            for (size in 5..maxSize) {
                // Update random subset of states
                val numUpdates = random.nextInt(size / 2) + 1
                println("Size $size of $maxSize, numUpdates $numUpdates")
                val updates = TreeMap<Long, Hash>()
                repeat(numUpdates) {
                    val pos = random.nextInt(size).toLong()
                    val digest = ds.digest(random.nextInt().toBigInteger().toByteArray())
                    updates[pos] = digest
                    states[pos] = digest
                    println("New state $pos: ${digest.toHex()}")
                }

                blockHeight++

                stateRootHash = snapshot.updateSnapshot(blockHeight, updates, protocolV2)
                //  snapshot.pruneSnapshot(blockHeight)

                val rightmost = states.keys.maxOrNull() ?: 0

                val allLeafHashesFromTree = snapshot.getAllLeafHashes(blockHeight)
                for (pos in 0L..rightmost) {
                    val stateAtPos = states[pos] ?: EMPTY_HASH
                    assertEquals(stateAtPos.wrap(), allLeafHashesFromTree[pos.toInt()].wrap())

                    val proof = snapshot.getMerkleProof(blockHeight, pos)
                    val merkleRoot = calculateMerkleRoot(proof, pos, stateAtPos)

                    if (stateRootHash.toHex() != merkleRoot.toHex()) {
                        analyzeMerkleProofDiscrepancy(snapshot, blockHeight, pos, stateAtPos)
                    }
                    assertEquals(stateRootHash.toHex(), merkleRoot.toHex())

                    val allLeavesWithValues = allLeafHashesFromTree.subList(0, rightmost.toInt() + 1)
                    val proofBuiltFromLeaves = rangeProofVerifier.calculateMerkleRoot(allLeavesWithValues, levelsPerPage)
                    assertEquals(merkleRoot.wrap(), proofBuiltFromLeaves.wrap())
                }
                // Verify that trailing leaves are all empty
                for (pos in rightmost + 1 until allLeafHashesFromTree.size) {
                    assertEquals(EMPTY_HASH.wrap(), allLeafHashesFromTree[pos.toInt()].wrap())
                }
            }

            // Compare against snapshot built afresh
            val stateRootHash2 = snapshotT.updateSnapshot(blockHeight, states, protocolV2)
            assertEquals(stateRootHash.toHex(), stateRootHash2.toHex())
        }
    }

    @Test
    fun testSequentialSnapshots() {
        val maxSize = 129L
        val prefix2 = "prefix2"
        val protocolV2 = 2

        runStorageCommand(appConfig, 0L) { ctx ->
            val db = DatabaseAccess.of(ctx).apply {
                initializeBlockchain(ctx, BlockchainRid.ZERO_RID)
                createPageTable(ctx, "${PREFIX}_snapshot")
                createStateLeafTable(ctx, PREFIX)
                createStateLeafTableIndex(ctx, PREFIX, 0)

                createPageTable(ctx, "${prefix2}_snapshot")
            }

            val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)
            val snapshot2 = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, prefix2)

            var stateRootHash = EMPTY_HASH
            val states = TreeMap<Long, Hash>()
            var blockHeight = 0L

            for (pos in 0 until maxSize) {
                blockHeight++
                val digest = ds.digest(pos.toBigInteger().toByteArray())
                val updates = TreeMap<Long, Hash>()
                updates[pos] = digest
                states[pos] = digest

                println("Adding new state $pos: ${digest.toHex()}")

                stateRootHash = snapshot.updateSnapshot(blockHeight, updates, protocolV2)
                // snapshot.pruneSnapshot(blockHeight)

                // Verify all proofs up to this point
                for (j in 0L..pos) {
                    val proof = snapshot.getMerkleProof(blockHeight, j)
                    val merkleRoot = calculateMerkleRoot(proof, j, states[j] ?: EMPTY_HASH)

                    if (stateRootHash.toHex() != merkleRoot.toHex()) {
                        analyzeMerkleProofDiscrepancy(snapshot, blockHeight, j, states[j] ?: EMPTY_HASH)
                    }
                    assertEquals(stateRootHash.toHex(), merkleRoot.toHex(), "Mismatch at blockHeight $blockHeight, position $j")
                }
            }

            // Compare against snapshot built afresh
            val stateRootHash2 = snapshot2.updateSnapshot(blockHeight, states, 2)
            assertEquals(stateRootHash.toHex(), stateRootHash2.toHex(), "Final root hash mismatch")
        }
    }


    private fun analyzeMerkleProofDiscrepancy(snapshot: SnapshotPageStore, blockHeight: Long, pos: Long, leaf: Hash) {
        println("\nAnalyzing Merkle proof for position $pos at block height $blockHeight")
        println("Leaf hash: ${leaf.toHex()}")

        val proof = snapshot.getMerkleProof(blockHeight, pos)
        var currentHash = leaf
        var prevPage: Page? = null

        proof.forEachIndexed { level, proofHash ->

            println("\nLevel $level:")
            println("Proof hash from path: ${proofHash.toHex()}")

            if (level % levelsPerPage == 0) {
                val leafsInPage = 1 shl (level + snapshot.levelsPerPage)
                val left = pos - (pos % leafsInPage)
                val leftInEntry = left / (1 shl level)
                // Get the actual page from storage
                val page = snapshot.readPage(blockHeight, level, leftInEntry)
                prevPage = page
                if (page != null) {
                    println("Page found at level $level, left: ${page.left}")
                    println("Page child hashes:")
                    page.childHashes.forEachIndexed { idx, hash ->
                        println("[$idx]: ${hash.toHex()}")
                    }
                } else {
                    println("No page found at level $level")
                }
            }

            // Calculate next hash
            currentHash = if (((pos shr level) and 1L) != 0L) {
                // If bit is 1, proof hash goes on left
                println("Computing hash: proofHash || currentHash")
                ds.hash(proofHash, currentHash)
            } else {
                // If bit is 0, proof hash goes on right
                println("Computing hash: currentHash || proofHash")
                ds.hash(currentHash, proofHash)
            }
            println("Resulting hash: ${currentHash.toHex()}")

            if (prevPage != null) {
                val leafsPerNode = 1L shl level
                val relPos = ((pos - prevPage.left * leafsPerNode) shr level).toInt()
                val childIndex = relPos % 2
                val leftHash = prevPage.getChildHash(level % levelsPerPage, ds::hash, childIndex)
                val rightHash = prevPage.getChildHash(level % levelsPerPage, ds::hash, childIndex + 1)
                val nodeHash = ds.hash(leftHash, rightHash)
                if (nodeHash.toHex() != currentHash.toHex()) {
                    println("Node hash does not match proof hash")
                    println("Node hash: ${nodeHash.toHex()}")
                    println("Proof hash: ${currentHash.toHex()}")
                    println("Left hash: ${leftHash.toHex()}")
                    println("Right hash: ${rightHash.toHex()}")
                    println("Relative position: $relPos")
                    println("Child index: $childIndex")
                }
            }
        }

        println("\nFinal root hash: ${currentHash.toHex()}")
    }
}
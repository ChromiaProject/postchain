package net.postchain.base.snapshot

import net.postchain.StorageBuilder
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.testDbConfig
import net.postchain.base.withWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.data.Hash
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.config.app.AppConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.TreeMap

class SnapshotIT: SnapshotBaseIT() {

    private val appConfig: AppConfig = testDbConfig("database_snapshot")

    @Test
    fun testSnapshot() {
        val storage = StorageBuilder.buildStorage(appConfig, wipeDatabase = true)
        val chainId = 0L

        withWriteConnection(storage, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.initializeBlockchain(ctx, BlockchainRid.ZERO_RID)
            db.apply {
                createPageTable(ctx, "${PREFIX}_snapshot")
                createStateLeafTable(ctx, PREFIX)
                createStateLeafTableIndex(ctx, PREFIX, 0)
            }
            val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)

            var blockHeight = 1L
            val states = TreeMap<Long, Hash>()
            states[0] = "044852b2a670ade5407e78fb2863c51de9fcb96542a07186fe3aeda6bb8a116d".hexStringToByteArray()
            states[1] = "c89efdaa54c0f20c7adf612882df0950f5a951637e0307cdcb4c672f298b8bc6".hexStringToByteArray()
            states[3] = "2a80e1ef1d7842f27f2e6be0972bb708b9a135c38860dbe73c27c3486c34f4de".hexStringToByteArray()
            states[4] = "13600b294191fc92924bb3ce4b969c1e7e2bab8f4c93c3fc6d0a51733df3c060".hexStringToByteArray()
            states[5] = "ceebf77a833b30520287ddd9478ff51abbdffa30aa90a8d655dba0e8a79ce0c1".hexStringToByteArray()
            states[6] = "e455bf8ea6e7463a1046a0b52804526e119b4bf5136279614e0b1e8e296a4e2d".hexStringToByteArray()
            states[7] = "52f1a9b320cab38e5da8a8f97989383aab0a49165fc91c737310e4f7e9821021".hexStringToByteArray()
            states[8] = "e4b1702d9298fee62dfeccc57d322a463ad55ca201256d01f62b45b2e1c21c10".hexStringToByteArray()
            states[9] = "d2f8f61201b2b11a78d6e866abc9c3db2ae8631fa656bfe5cb53668255367afb".hexStringToByteArray()
            states.forEach { (n, data) ->  db.insertState(ctx, PREFIX, blockHeight, n, data) }
            states.forEach { (n, data) ->
                val accountState = db.getAccountState(ctx, PREFIX, blockHeight, n)
                assertNotNull(accountState)
                assertTrue(accountState!!.data.contentEquals(data))
            }
            var stateRootHash = snapshot.updateSnapshot(blockHeight, states)
            snapshot.pruneSnapshot(blockHeight)
            var l01 = ds.hash(states[0]!!, states[1]!!)
            var l23 = ds.hash(EMPTY_HASH, states[3]!!)
            var hash00 = ds.hash(l01, l23)
            var l45 = ds.hash(states[4]!!, states[5]!!)
            var l67 = ds.hash(states[6]!!, states[7]!!)
            var hash01 = ds.hash(l45, l67)
            var l89 = ds.hash(states[8]!!, states[9]!!)
            var hash10 = ds.hash(l89, EMPTY_HASH)
            var hash11 = ds.hash(EMPTY_HASH, EMPTY_HASH)
            var left = ds.hash(hash00, hash01)
            var right = ds.hash(hash10, hash11)
            var root = ds.hash(left, right)
            assertEquals(stateRootHash.toHex(), root.toHex())
            assertEquals(root.toHex(), stateRootHash.toHex())
            val page = snapshot.readPage(1, 2, 0)
            assertEquals(page!!.childHashes[0].toHex(), hash00.toHex())

            blockHeight++
            val states2 = TreeMap<Long, Hash>()
            states2[2] = "ad7c5bef027816a800da1736444fb58a807ef4c9603b7848673f7e3a68eb14a5".hexStringToByteArray()
            states2[8] = "9ae6066ff8547d3138cce35b150f93047df88fa376c8808f462d3bbdbcb4a690".hexStringToByteArray()
            states2[9] = "c41c8390c0da0418b7667ee0df6c246c26c4be6c0618368dce5a916e8008b0db".hexStringToByteArray()
            states2.forEach { (n, data) ->  db.insertState(ctx, PREFIX, blockHeight, n, data) }
            states2.forEach { (n, data) ->
                val accountState = db.getAccountState(ctx, PREFIX, blockHeight, n)
                assertNotNull(accountState)
                assertTrue(accountState!!.data.contentEquals(data))
            }
            stateRootHash = snapshot.updateSnapshot(blockHeight, states2)
            snapshot.pruneSnapshot(blockHeight)
            assertEquals(2, snapshot.highestLevelPage(blockHeight))

            l01 = ds.hash(states[0]!!, states[1]!!)
            l23 = ds.hash(states2[2]!!, states[3]!!)
            hash00 = ds.hash(l01, l23)
            l45 = ds.hash(states[4]!!, states[5]!!)
            l67 = ds.hash(states[6]!!, states[7]!!)
            hash01 = ds.hash(l45, l67)
            l89 = ds.hash(states2[8]!!, states2[9]!!)
            hash10 = ds.hash(l89, EMPTY_HASH)
            hash11 = ds.hash(EMPTY_HASH, EMPTY_HASH)

            left = ds.hash(hash00, hash01)
            right = ds.hash(hash10, hash11)
            root = ds.hash(left, right)
            assertEquals(stateRootHash.toHex(), root.toHex())

            blockHeight++
            val states3 = TreeMap<Long, Hash>()
            states3[10] = "1a192fabce13988b84994d4296e6cdc418d55e2f1d7f942188d4040b94fc57ac".hexStringToByteArray()
            states3[11] = "7880aec93413f117ef14bd4e6d130875ab2c7d7d55a064fac3c2f7bd51516380".hexStringToByteArray()
            states3.forEach { (n, data) ->  db.insertState(ctx, PREFIX, blockHeight, n, data) }
            states3.forEach { (n, data) ->
                val accountState = db.getAccountState(ctx, PREFIX, blockHeight, n)
                assertNotNull(accountState)
                assertTrue(accountState!!.data.contentEquals(data))
            }
            snapshot.updateSnapshot(blockHeight, states3)
            snapshot.pruneSnapshot(blockHeight)
            assertEquals(2, snapshot.highestLevelPage(blockHeight))

            blockHeight++
            val states4 = TreeMap<Long, Hash>()
            states4[0] = "8c18210df0d9514f2d2e5d8ca7c100978219ee80d3968ad850ab5ead208287b3".hexStringToByteArray()
            states4[12] = "7f8b6b088b6d74c2852fc86c796dca07b44eed6fb3daf5e6b59f7c364db14528".hexStringToByteArray()
            states4[13] = "789bcdf275fa270780a52ae3b79bb1ce0fda7e0aaad87b57b74bb99ac290714a".hexStringToByteArray()
            states4[14] = "5c4c6aa067b6f8e6cb38e6ab843832a94d1712d661a04d73c517d6a1931a9e5d".hexStringToByteArray()
            states4[15] = "1d3be50b2bb17407dd170f1d5da128d1def30c6b1598d6a629e79b4775265526".hexStringToByteArray()
            states4.forEach { (n, data) ->  db.insertState(ctx, PREFIX, blockHeight, n, data) }
            states4.forEach { (n, data) ->
                val accountState = db.getAccountState(ctx, PREFIX, blockHeight, n)
                assertNotNull(accountState)
                assertTrue(accountState!!.data.contentEquals(data))
            }
            stateRootHash = snapshot.updateSnapshot(blockHeight, states4)
            val oldStateRootHash = stateRootHash
            snapshot.pruneSnapshot(blockHeight)
            assertEquals(2, snapshot.highestLevelPage(blockHeight))

            l01 = ds.hash(states4[0]!!, states[1]!!)
            l23 = ds.hash(states2[2]!!, states[3]!!)
            hash00 = ds.hash(l01, l23)
            l45 = ds.hash(states[4]!!, states[5]!!)
            l67 = ds.hash(states[6]!!, states[7]!!)
            hash01 = ds.hash(l45, l67)
            l89 = ds.hash(states2[8]!!, states2[9]!!)
            val l1011 = ds.hash(states3[10]!!, states3[11]!!)
            hash10 = ds.hash(l89, l1011)
            val l1213 = ds.hash(states4[12]!!, states4[13]!!)
            val l1415 = ds.hash(states4[14]!!, states4[15]!!)
            hash11 = ds.hash(l1213, l1415)
            left = ds.hash(hash00, hash01)
            right = ds.hash(hash10, hash11)
            root = ds.hash(left, right)
            assertEquals(stateRootHash.toHex(), root.toHex())

            blockHeight++
            val states5 = TreeMap<Long, Hash>()
            states5[16] = "277ab82e5a4641341820a4a2933a62c1de997e42e92548657ae21b3728d580fe".hexStringToByteArray()
            states5.forEach { (n, data) ->  db.insertState(ctx, PREFIX, blockHeight, n, data) }
            states5.forEach { (n, data) ->
                val accountState = db.getAccountState(ctx, PREFIX, blockHeight, n)
                assertNotNull(accountState)
                assertTrue(accountState!!.data.contentEquals(data))
            }
            stateRootHash = snapshot.updateSnapshot(blockHeight, states5)
            snapshot.pruneSnapshot(blockHeight)
            assertEquals(4, snapshot.highestLevelPage(blockHeight))
            assertEquals(4, snapshot.highestLevelPage(blockHeight+100))

            val p5 = ds.hash(ds.hash(states5[16]!!, EMPTY_HASH), EMPTY_HASH)
            val p7 = ds.hash(ds.hash(p5, EMPTY_HASH), EMPTY_HASH)
            root = ds.hash(ds.hash(root, p7), EMPTY_HASH)
            assertEquals(stateRootHash.toHex(), root.toHex())

            assertNotNull(snapshot.readPage(blockHeight, 2, 0))
            assertNotNull(snapshot.readPage(blockHeight-1, 2, 0))
            assertEquals(null, snapshot.readPage(blockHeight-snapshotsToKeep, 2, 0))

            // Verify merkle proofs
            var leafPos = 0L
            var proofs = snapshot.getMerkleProof(blockHeight-snapshotsToKeep, leafPos)
            assertEquals(0, proofs.size)

            proofs = snapshot.getMerkleProof(blockHeight-1, leafPos)
            var merkleRoot = getMerkleRoot(proofs, leafPos.toInt(), states4[leafPos]!!)
            assertEquals(oldStateRootHash.toHex(), merkleRoot.toHex())

            proofs = snapshot.getMerkleProof(blockHeight, leafPos)
            merkleRoot = getMerkleRoot(proofs, leafPos.toInt(), states4[leafPos]!!)
            assertEquals(stateRootHash.toHex(), merkleRoot.toHex())

            leafPos = 2L
            proofs = snapshot.getMerkleProof(blockHeight-snapshotsToKeep, leafPos)
            assertEquals(0, proofs.size)

            proofs = snapshot.getMerkleProof(blockHeight-1, leafPos)
            merkleRoot = getMerkleRoot(proofs, leafPos.toInt(), states2[leafPos]!!)
            assertEquals(oldStateRootHash.toHex(), merkleRoot.toHex())

            proofs = snapshot.getMerkleProof(blockHeight, leafPos)
            merkleRoot = getMerkleRoot(proofs, leafPos.toInt(), states2[leafPos]!!)
            assertEquals(stateRootHash.toHex(), merkleRoot.toHex())

            leafPos = 16L
            proofs = snapshot.getMerkleProof(blockHeight-1, leafPos)
            assertEquals(0, proofs.size)

            proofs = snapshot.getMerkleProof(blockHeight, leafPos)
            merkleRoot = getMerkleRoot(proofs, leafPos.toInt(), states5[leafPos]!!)
            assertEquals(stateRootHash.toHex(), merkleRoot.toHex())
            true
        }
    }

    @Test
    fun testSnapshot128() {
        val storage = StorageBuilder.buildStorage(appConfig, wipeDatabase = true)
        val chainId = 0L

        withWriteConnection(storage, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.initializeBlockchain(ctx, BlockchainRid.ZERO_RID)
            db.apply {
                createPageTable(ctx, "${PREFIX}_snapshot")
                createStateLeafTable(ctx, PREFIX)
                createStateLeafTableIndex(ctx, PREFIX, 0)
            }
            val snapshot = SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)
            val states = TreeMap<Long, Hash>()
            for (i in 0..127) {
                states[i.toLong()] = ds.digest(i.toBigInteger().toByteArray())
            }
            val blockHeight = 1L

            states.forEach { (n, data) ->  db.insertState(ctx, PREFIX, blockHeight, n, data) }
            states.forEach { (n, data) ->
                val accountState = db.getAccountState(ctx, PREFIX, blockHeight, n)
                assertNotNull(accountState)
                assertTrue(accountState!!.data.contentEquals(data))
            }

            val stateRootHash = snapshot.updateSnapshot(blockHeight, states)
            snapshot.pruneSnapshot(blockHeight)

            arrayOf(0, 1, 36, 88, 126, 127).forEach {
                val proofs = snapshot.getMerkleProof(blockHeight, it.toLong())
                val expected = getMerkleRoot(proofs, it, states[it.toLong()]!!)
                assertEquals(expected.toHex(), stateRootHash.toHex())
            }
            true
        }
    }
}
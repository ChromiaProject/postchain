// Copyright (c) 2021 ChromaWay AB. See README for license information.

package net.postchain.base.snapshot

import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.data.Hash
import net.postchain.common.data.SHA256
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.core.ExecutionContext
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.math.BigInteger
import java.security.MessageDigest
import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals

class MerkleTest {

    private val leafHashes = TreeMap<Long, Hash>()
    private val ds = SimpleDigestSystem(MessageDigest.getInstance(SHA256))

    private lateinit var snapshot: TestSnapshotPageStore
    private lateinit var event: TestEventPageStore

    @BeforeEach
    fun setUp() {
        val ctx = mock<ExecutionContext>()
        snapshot = TestSnapshotPageStore(ctx, 2, ds)
        event = TestEventPageStore(ctx, 2, ds)
        leafHashes[0] = "044852b2a670ade5407e78fb2863c51de9fcb96542a07186fe3aeda6bb8a116d".hexStringToByteArray()
        leafHashes[1] = "c89efdaa54c0f20c7adf612882df0950f5a951637e0307cdcb4c672f298b8bc6".hexStringToByteArray()
        leafHashes[3] = "2a80e1ef1d7842f27f2e6be0972bb708b9a135c38860dbe73c27c3486c34f4de".hexStringToByteArray()
        leafHashes[4] = "13600b294191fc92924bb3ce4b969c1e7e2bab8f4c93c3fc6d0a51733df3c060".hexStringToByteArray()
        leafHashes[5] = "ceebf77a833b30520287ddd9478ff51abbdffa30aa90a8d655dba0e8a79ce0c1".hexStringToByteArray()
        leafHashes[6] = "e455bf8ea6e7463a1046a0b52804526e119b4bf5136279614e0b1e8e296a4e2d".hexStringToByteArray()
        leafHashes[7] = "52f1a9b320cab38e5da8a8f97989383aab0a49165fc91c737310e4f7e9821021".hexStringToByteArray()
    }

    @Test
    fun testUpdateSnapshot_3pages() {
        val stateRootHash = snapshot.updateSnapshot( 1, leafHashes)
        val l01 = ds.hash(leafHashes[0]!!, leafHashes[1]!!)
        val l23 = ds.hash(EMPTY_HASH, leafHashes[3]!!)
        val hash00 = ds.hash(l01, l23)
        val l45 = ds.hash(leafHashes[4]!!, leafHashes[5]!!)
        val l67 = ds.hash(leafHashes[6]!!, leafHashes[7]!!)
        val hash01 = ds.hash(l45, l67)
        val left = ds.hash(hash00, hash01)
        val right  = ds.hash(EMPTY_HASH, EMPTY_HASH)
        val root = ds.hash(left, right)
        assertEquals(stateRootHash.toHex(), root.toHex())
    }

    @Test
    fun testUpdateSnapshot_4pages() {
        leafHashes[8] = "e4b1702d9298fee62dfeccc57d322a463ad55ca201256d01f62b45b2e1c21c10".hexStringToByteArray()
        leafHashes[9] = "d2f8f61201b2b11a78d6e866abc9c3db2ae8631fa656bfe5cb53668255367afb".hexStringToByteArray()
        val stateRootHash = snapshot.updateSnapshot( 1, leafHashes)
        val l01 = ds.hash(leafHashes[0]!!, leafHashes[1]!!)
        val l23 = ds.hash(EMPTY_HASH, leafHashes[3]!!)
        val hash00 = ds.hash(l01, l23)
        val l45 = ds.hash(leafHashes[4]!!, leafHashes[5]!!)
        val l67 = ds.hash(leafHashes[6]!!, leafHashes[7]!!)
        val hash01 = ds.hash(l45, l67)

        val l89 = ds.hash(leafHashes[8]!!, leafHashes[9]!!)
        val hash10 = ds.hash(l89, EMPTY_HASH)
        val hash11 = ds.hash(EMPTY_HASH, EMPTY_HASH)
        val left = ds.hash(hash00, hash01)
        val right = ds.hash(hash10, hash11)
        val root = ds.hash(left, right)
        assertEquals(stateRootHash.toHex(), root.toHex())
    }

    @Test
    fun testUpdateSnapshot_4pages_Multiple_Blocks() {
        leafHashes[8] = "e4b1702d9298fee62dfeccc57d322a463ad55ca201256d01f62b45b2e1c21c10".hexStringToByteArray()
        leafHashes[9] = "d2f8f61201b2b11a78d6e866abc9c3db2ae8631fa656bfe5cb53668255367afb".hexStringToByteArray()
        snapshot.updateSnapshot(1, leafHashes)

        val leafHashes2 = TreeMap<Long, Hash>()
        leafHashes2[2] = "ad7c5bef027816a800da1736444fb58a807ef4c9603b7848673f7e3a68eb14a5".hexStringToByteArray()
        leafHashes2[8] = "9ae6066ff8547d3138cce35b150f93047df88fa376c8808f462d3bbdbcb4a690".hexStringToByteArray()
        leafHashes2[9] = "c41c8390c0da0418b7667ee0df6c246c26c4be6c0618368dce5a916e8008b0db".hexStringToByteArray()

        val stateRootHash = snapshot.updateSnapshot( 2, leafHashes2)

        val l01 = ds.hash(leafHashes[0]!!, leafHashes[1]!!)
        val l23 = ds.hash(leafHashes2[2]!!, leafHashes[3]!!)
        val hash00 = ds.hash(l01, l23)
        val l45 = ds.hash(leafHashes[4]!!, leafHashes[5]!!)
        val l67 = ds.hash(leafHashes[6]!!, leafHashes[7]!!)
        val hash01 = ds.hash(l45, l67)

        val l89 = ds.hash(leafHashes2[8]!!, leafHashes2[9]!!)
        val hash10 = ds.hash(l89, EMPTY_HASH)
        val hash11 = ds.hash(EMPTY_HASH, EMPTY_HASH)
        val left = ds.hash(hash00, hash01)
        val right = ds.hash(hash10, hash11)
        val root = ds.hash(left, right)
        assertEquals(stateRootHash.toHex(), root.toHex())
    }

    @Test
    fun testUpdateSnapshot_6pages_Multiple_Blocks() {
        leafHashes[8] = "e4b1702d9298fee62dfeccc57d322a463ad55ca201256d01f62b45b2e1c21c10".hexStringToByteArray()
        leafHashes[9] = "d2f8f61201b2b11a78d6e866abc9c3db2ae8631fa656bfe5cb53668255367afb".hexStringToByteArray()
        snapshot.updateSnapshot(1, leafHashes)

        val leafHashes2 = TreeMap<Long, Hash>()
        leafHashes2[2] = "ad7c5bef027816a800da1736444fb58a807ef4c9603b7848673f7e3a68eb14a5".hexStringToByteArray()
        leafHashes2[8] = "9ae6066ff8547d3138cce35b150f93047df88fa376c8808f462d3bbdbcb4a690".hexStringToByteArray()
        leafHashes2[9] = "c41c8390c0da0418b7667ee0df6c246c26c4be6c0618368dce5a916e8008b0db".hexStringToByteArray()

        snapshot.updateSnapshot(2, leafHashes2)

        val leafHashes3 = TreeMap<Long, Hash>()
        leafHashes3[10] = "1a192fabce13988b84994d4296e6cdc418d55e2f1d7f942188d4040b94fc57ac".hexStringToByteArray()
        leafHashes3[11] = "7880aec93413f117ef14bd4e6d130875ab2c7d7d55a064fac3c2f7bd51516380".hexStringToByteArray()

        snapshot.updateSnapshot(3, leafHashes3)

        val leafHashes4 = TreeMap<Long, Hash>()

        leafHashes4[0] = "8c18210df0d9514f2d2e5d8ca7c100978219ee80d3968ad850ab5ead208287b3".hexStringToByteArray()
        leafHashes4[12] = "7f8b6b088b6d74c2852fc86c796dca07b44eed6fb3daf5e6b59f7c364db14528".hexStringToByteArray()
        leafHashes4[13] = "789bcdf275fa270780a52ae3b79bb1ce0fda7e0aaad87b57b74bb99ac290714a".hexStringToByteArray()
        leafHashes4[14] = "5c4c6aa067b6f8e6cb38e6ab843832a94d1712d661a04d73c517d6a1931a9e5d".hexStringToByteArray()
        leafHashes4[15] = "1d3be50b2bb17407dd170f1d5da128d1def30c6b1598d6a629e79b4775265526".hexStringToByteArray()

        val stateRootHash = snapshot.updateSnapshot(4, leafHashes4)

        val l01 = ds.hash(leafHashes4[0]!!, leafHashes[1]!!)
        val l23 = ds.hash(leafHashes2[2]!!, leafHashes[3]!!)
        val hash00 = ds.hash(l01, l23)
        val l45 = ds.hash(leafHashes[4]!!, leafHashes[5]!!)
        val l67 = ds.hash(leafHashes[6]!!, leafHashes[7]!!)
        val hash01 = ds.hash(l45, l67)
        val l89 = ds.hash(leafHashes2[8]!!, leafHashes2[9]!!)
        val l1011 = ds.hash(leafHashes3[10]!!, leafHashes3[11]!!)
        val hash10 = ds.hash(l89, l1011)
        val l1213 = ds.hash(leafHashes4[12]!!, leafHashes4[13]!!)
        val l1415 = ds.hash(leafHashes4[14]!!, leafHashes4[15]!!)
        val hash11 = ds.hash(l1213, l1415)
        val leftHash = ds.hash(hash00, hash01)
        val rightHash = ds.hash(hash10, hash11)
        val root = ds.hash(leftHash, rightHash)
        assertEquals(stateRootHash.toHex(), root.toHex())

        val leafHashes5 = TreeMap<Long, Hash>()
        leafHashes5[16] = "277ab82e5a4641341820a4a2933a62c1de997e42e92548657ae21b3728d580fe".hexStringToByteArray()

        val stateRootHash2 = snapshot.updateSnapshot(5, leafHashes5)

        val p5 = ds.hash(ds.hash(leafHashes5[16]!!, EMPTY_HASH), EMPTY_HASH)
        val p7 = ds.hash(ds.hash(p5, EMPTY_HASH), EMPTY_HASH)
        val root2 = ds.hash(ds.hash(root, p7), EMPTY_HASH)
        assertEquals(stateRootHash2.toHex(), root2.toHex())
    }

    @Test
    fun testUpdateSnapshot_6pages_Single_Block() {
        leafHashes[2] = "ad7c5bef027816a800da1736444fb58a807ef4c9603b7848673f7e3a68eb14a5".hexStringToByteArray()
        leafHashes[8] = "e4b1702d9298fee62dfeccc57d322a463ad55ca201256d01f62b45b2e1c21c10".hexStringToByteArray()
        leafHashes[9] = "d2f8f61201b2b11a78d6e866abc9c3db2ae8631fa656bfe5cb53668255367afb".hexStringToByteArray()
        leafHashes[10] = "1a192fabce13988b84994d4296e6cdc418d55e2f1d7f942188d4040b94fc57ac".hexStringToByteArray()
        leafHashes[11] = "7880aec93413f117ef14bd4e6d130875ab2c7d7d55a064fac3c2f7bd51516380".hexStringToByteArray()
        leafHashes[12] = "7f8b6b088b6d74c2852fc86c796dca07b44eed6fb3daf5e6b59f7c364db14528".hexStringToByteArray()
        leafHashes[13] = "789bcdf275fa270780a52ae3b79bb1ce0fda7e0aaad87b57b74bb99ac290714a".hexStringToByteArray()
        leafHashes[14] = "5c4c6aa067b6f8e6cb38e6ab843832a94d1712d661a04d73c517d6a1931a9e5d".hexStringToByteArray()
        leafHashes[15] = "1d3be50b2bb17407dd170f1d5da128d1def30c6b1598d6a629e79b4775265526".hexStringToByteArray()
        leafHashes[16] = "277ab82e5a4641341820a4a2933a62c1de997e42e92548657ae21b3728d580fe".hexStringToByteArray()

        val l01 = ds.hash(leafHashes[0]!!, leafHashes[1]!!)
        val l23 = ds.hash(leafHashes[2]!!, leafHashes[3]!!)
        val hash00 = ds.hash(l01, l23)
        val l45 = ds.hash(leafHashes[4]!!, leafHashes[5]!!)
        val l67 = ds.hash(leafHashes[6]!!, leafHashes[7]!!)
        val hash01 = ds.hash(l45, l67)
        val l89 = ds.hash(leafHashes[8]!!, leafHashes[9]!!)
        val l1011 = ds.hash(leafHashes[10]!!, leafHashes[11]!!)
        val hash10 = ds.hash(l89, l1011)
        val l1213 = ds.hash(leafHashes[12]!!, leafHashes[13]!!)
        val l1415 = ds.hash(leafHashes[14]!!, leafHashes[15]!!)
        val hash11 = ds.hash(l1213, l1415)
        val leftHash = ds.hash(hash00, hash01)
        val rightHash = ds.hash(hash10, hash11)

        val p5 = ds.hash(ds.hash(leafHashes[16]!!, EMPTY_HASH), EMPTY_HASH)
        val p7 = ds.hash(ds.hash(p5, EMPTY_HASH), EMPTY_HASH)
        val p6 = ds.hash(leftHash, rightHash)
        val root = ds.hash(ds.hash(p6, p7), EMPTY_HASH)
        val stateRootHash = snapshot.updateSnapshot(1, leafHashes)
        assertEquals(stateRootHash.toHex(), root.toHex())
    }

    @Test
    fun testGetMerkleProof8_Proof5() {
        val leafs = TreeMap<Long, Hash>()
        for (i in 0..7) {
            leafs[i.toLong()] = ds.digest(BigInteger.valueOf(i.toLong()).toByteArray())
        }

        val leafPos = 5L
        val stateRootHash = snapshot.updateSnapshot(0, leafs)
        val proofs = snapshot.getMerkleProof(0, leafPos)
        val root = getMerkleProof(proofs, leafPos.toInt(), leafs[leafPos]!!)
        assertEquals(stateRootHash.toHex(), root.toHex())
    }

    @Test
    fun testGetMerkleProof8_Proof5_With_Empty_Hash_Leaf() {
        val leafs = TreeMap<Long, Hash>()
        for (i in 0..7) {
            leafs[i.toLong()] = ds.digest(BigInteger.valueOf(i.toLong()).toByteArray())
        }
        leafs[4] = EMPTY_HASH

        val leafPos = 5L
        val stateRootHash = snapshot.updateSnapshot(0, leafs)
        val proofs = snapshot.getMerkleProof(0, leafPos)
        val root = getMerkleProof(proofs, leafPos.toInt(), leafs[leafPos]!!)
        assertEquals(stateRootHash.toHex(), root.toHex())
    }

    @Test
    fun testGetMerkleProof8_Proof8() {
        val leafs = TreeMap<Long, Hash>()
        for (i in 0..8) {
            leafs[i.toLong()] = ds.digest(BigInteger.valueOf(i.toLong()).toByteArray())
        }

        val leafPos = 8L
        val stateRootHash = snapshot.updateSnapshot(0, leafs)
        val proofs = snapshot.getMerkleProof(0, leafPos)
        val root = getMerkleProof(proofs, leafPos.toInt(), leafs[leafPos]!!)
        assertEquals(stateRootHash.toHex(), root.toHex())
    }

    @Test
    fun testGetMerkleProof16_Proof5() {
        val leafs = TreeMap<Long, Hash>()
        for (i in 0..16) {
            leafs[i.toLong()] = ds.digest(BigInteger.valueOf(i.toLong()).toByteArray())
        }

        val leafPos = 5L
        val stateRootHash = snapshot.updateSnapshot(0, leafs)
        val proofs = snapshot.getMerkleProof(0, leafPos)
        val root = getMerkleProof(proofs, leafPos.toInt(), leafs[leafPos]!!)
        assertEquals(stateRootHash.toHex(), root.toHex())
    }

    @Test
    fun testGetMerkleProof16_Proof5_With_Empty_Leafs() {
        val leafs = TreeMap<Long, Hash>()
        for (i in 0..16) {
            leafs[i.toLong()] = ds.digest(BigInteger.valueOf(i.toLong()).toByteArray())
        }

        leafs[4] = EMPTY_HASH
        leafs[6] = EMPTY_HASH
        leafs[7] = EMPTY_HASH

        val leafPos = 5L
        val stateRootHash = snapshot.updateSnapshot(0, leafs)
        val proofs = snapshot.getMerkleProof(0, leafPos)
        val root = getMerkleProof(proofs, leafPos.toInt(), leafs[leafPos]!!)
        assertEquals(stateRootHash.toHex(), root.toHex())
    }

    @Test
    fun testGetMerkleProof16_Proof16() {
        val leafs = TreeMap<Long, Hash>()
        for (i in 0..16) {
            leafs[i.toLong()] = ds.digest(BigInteger.valueOf(i.toLong()).toByteArray())
        }
        val leafPos = 16L
        val stateRootHash = snapshot.updateSnapshot(0, leafs)
        val proofs = snapshot.getMerkleProof(0, leafPos)
        val root = getMerkleProof(proofs, leafPos.toInt(), leafs[leafPos]!!)
        assertEquals(stateRootHash.toHex(), root.toHex())
    }

    @Test
    fun testGetMerkleProof_32_Proof8() {
        val leafs = TreeMap<Long, Hash>()
        for (i in 0..31) {
            leafs[i.toLong()] = ds.digest(BigInteger.valueOf(i.toLong()).toByteArray())
        }

        val leafPos = 8L
        val stateRootHash = snapshot.updateSnapshot(0, leafs)
        val proofs = snapshot.getMerkleProof(0, leafPos)
        val root = getMerkleProof(proofs, leafPos.toInt(), leafs[leafPos]!!)
        assertEquals(stateRootHash.toHex(), root.toHex())
    }

    @Test
    fun testWriteEventTree_4_Leafs() {
        val leafs = arrayListOf<Hash>()
        for (i in 1..4) {
            leafs.add(ds.digest(BigInteger.valueOf(i.toLong()).toByteArray()))
        }
        val root = event.writeEventTree(0, leafs)

        val l12 = ds.hash(leafs[0], leafs[1])
        val l34 = ds.hash(leafs[2], leafs[3])
        val expected = ds.hash(l12, l34)

        assertEquals(expected.toHex(), root.toHex())
    }

    @Test
    fun testWriteEventTree_9_Leafs() {
        val leafs = arrayListOf<Hash>()
        for (i in 1..9) {
            leafs.add(ds.digest(BigInteger.valueOf(i.toLong()).toByteArray()))
        }
        val root = event.writeEventTree(0, leafs)

        val l12 = ds.hash(leafs[0], leafs[1])
        val l34 = ds.hash(leafs[2], leafs[3])
        val l56 = ds.hash(leafs[4], leafs[5])
        val l78 = ds.hash(leafs[6], leafs[7])
        val left = ds.hash(l12, l34)
        val right = ds.hash(l56, l78)
        val p3 = ds.hash(ds.hash(leafs[8], EMPTY_HASH), EMPTY_HASH)
        val expected = ds.hash(ds.hash(left, right), ds.hash(p3, EMPTY_HASH))

        assertEquals(expected.toHex(), root.toHex())
    }

    @Test
    fun testGetMerkleProofForEvent_4_Leafs() {
        val leafs = arrayListOf<Hash>()
        for (i in 1..4) {
            leafs.add(ds.digest(BigInteger.valueOf(i.toLong()).toByteArray()))
        }
        val pos = 2L
        val root = event.writeEventTree(0, leafs)
        val proofs = event.getMerkleProof(0, pos)
        val expected = getMerkleProof(proofs, pos.toInt(), leafs[pos.toInt()])
        assertEquals(2, proofs.size)
        assertEquals(expected.toHex(), root.toHex())
    }

    @Test
    fun testGetMerkleProofForEvent_9_Leafs() {
        val leafs = arrayListOf<Hash>()
        for (i in 1..9) {
            leafs.add(ds.digest(BigInteger.valueOf(i.toLong()).toByteArray()))
        }

        val pos = 4L
        val root = event.writeEventTree(0, leafs)
        val proofs = event.getMerkleProof(0, pos)
        val expected = getMerkleProof(proofs, pos.toInt(), leafs[pos.toInt()])

        assertEquals(4, proofs.size)
        assertEquals(expected.toHex(), root.toHex())
    }

    @Test
    fun testGetMerkleProofForEvent_RealData() {
        val leafs = arrayListOf<Hash>()
        val data = GtvFactory.gtv(GtvFactory.gtv("000000000000000000000000e35487517b1bee0e22daf706a82f1d3d1fd963fd".hexStringToByteArray()),
            GtvFactory.gtv("000000000000000000000000e105ba42b66d08ac7ca7fc48c583599044a6dab3".hexStringToByteArray()),
            GtvFactory.gtv(300L)
        )
        leafs.add(ds.digest(GtvEncoder.encodeGtv(data)))
        val pos = 0L
        val root = event.writeEventTree(0, leafs)
        val proofs = event.getMerkleProof(0, pos)
        val expected = getMerkleProof(proofs, pos.toInt(), leafs[pos.toInt()])

        assertEquals(2, proofs.size)
        assertEquals(expected.toHex(), root.toHex())
    }

    private fun getMerkleProof(proofs: List<Hash>, pos: Int, leaf: Hash): Hash {
        var r = leaf
        proofs.forEachIndexed { i, h ->
            r = if (((pos shr i) and 1) != 0) {
                ds.hash(h, r)
            } else {
                ds.hash(r, h)
            }
        }
        return r
    }
}
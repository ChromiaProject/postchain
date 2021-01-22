package net.postchain.base.snapshot

import junit.framework.TestCase
import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.data.Hash
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.crypto.SECP256K1Keccak
import java.util.*

class MerkleTest : TestCase() {

    private lateinit var store: TestPageStore
    private val leafHashes = TreeMap<Long, Hash>()

    public override fun setUp() {
        super.setUp()
        store = TestPageStore(2, SECP256K1Keccak::treeHasher)
        leafHashes[0] = "044852b2a670ade5407e78fb2863c51de9fcb96542a07186fe3aeda6bb8a116d".hexStringToByteArray()
        leafHashes[1] = "c89efdaa54c0f20c7adf612882df0950f5a951637e0307cdcb4c672f298b8bc6".hexStringToByteArray()
        leafHashes[3] = "2a80e1ef1d7842f27f2e6be0972bb708b9a135c38860dbe73c27c3486c34f4de".hexStringToByteArray()
        leafHashes[4] = "13600b294191fc92924bb3ce4b969c1e7e2bab8f4c93c3fc6d0a51733df3c060".hexStringToByteArray()
        leafHashes[5] = "ceebf77a833b30520287ddd9478ff51abbdffa30aa90a8d655dba0e8a79ce0c1".hexStringToByteArray()
        leafHashes[6] = "e455bf8ea6e7463a1046a0b52804526e119b4bf5136279614e0b1e8e296a4e2d".hexStringToByteArray()
        leafHashes[7] = "52f1a9b320cab38e5da8a8f97989383aab0a49165fc91c737310e4f7e9821021".hexStringToByteArray()
    }

    fun testUpdateSnapshot_3pages() {
        val stateRootHash = updateSnapshot(store, 1, leafHashes)
        val l01 = SECP256K1Keccak.treeHasher(leafHashes[0]!!, leafHashes[1]!!)
        val l23 = SECP256K1Keccak.treeHasher(EMPTY_HASH, leafHashes[3]!!)
        val hash00 = SECP256K1Keccak.treeHasher(l01, l23)
        val l45 = SECP256K1Keccak.treeHasher(leafHashes[4]!!, leafHashes[5]!!)
        val l67 = SECP256K1Keccak.treeHasher(leafHashes[6]!!, leafHashes[7]!!)
        val hash01 = SECP256K1Keccak.treeHasher(l45, l67)

        val root = SECP256K1Keccak.treeHasher(hash00, hash01)
        assertEquals(stateRootHash.toHex(), root.toHex())
    }

    fun testUpdateSnapshot_4pages() {
        leafHashes[8] = "e4b1702d9298fee62dfeccc57d322a463ad55ca201256d01f62b45b2e1c21c10".hexStringToByteArray()
        leafHashes[9] = "d2f8f61201b2b11a78d6e866abc9c3db2ae8631fa656bfe5cb53668255367afb".hexStringToByteArray()
        val stateRootHash = updateSnapshot(store, 1, leafHashes)
        val l01 = SECP256K1Keccak.treeHasher(leafHashes[0]!!, leafHashes[1]!!)
        val l23 = SECP256K1Keccak.treeHasher(EMPTY_HASH, leafHashes[3]!!)
        val hash00 = SECP256K1Keccak.treeHasher(l01, l23)
        val l45 = SECP256K1Keccak.treeHasher(leafHashes[4]!!, leafHashes[5]!!)
        val l67 = SECP256K1Keccak.treeHasher(leafHashes[6]!!, leafHashes[7]!!)
        val hash01 = SECP256K1Keccak.treeHasher(l45, l67)

        val l89 = SECP256K1Keccak.treeHasher(leafHashes[8]!!, leafHashes[9]!!)
        val hash10 = SECP256K1Keccak.treeHasher(l89, EMPTY_HASH)
        val hash11 = SECP256K1Keccak.treeHasher(EMPTY_HASH, EMPTY_HASH)
        val leftHash = SECP256K1Keccak.treeHasher(hash00, hash01)
        val rightHash = SECP256K1Keccak.treeHasher(hash10, hash11)
        val root = SECP256K1Keccak.treeHasher(leftHash, rightHash)
        assertEquals(stateRootHash.toHex(), root.toHex())
    }

    fun testUpdateSnapshot_4pages_Multiple_Blocks() {
        leafHashes[8] = "e4b1702d9298fee62dfeccc57d322a463ad55ca201256d01f62b45b2e1c21c10".hexStringToByteArray()
        leafHashes[9] = "d2f8f61201b2b11a78d6e866abc9c3db2ae8631fa656bfe5cb53668255367afb".hexStringToByteArray()
        updateSnapshot(store, 1, leafHashes)

        val leafHashes2 = TreeMap<Long, Hash>()
        leafHashes2[2] = "ad7c5bef027816a800da1736444fb58a807ef4c9603b7848673f7e3a68eb14a5".hexStringToByteArray()
        leafHashes2[8] = "9ae6066ff8547d3138cce35b150f93047df88fa376c8808f462d3bbdbcb4a690".hexStringToByteArray()
        leafHashes2[9] = "c41c8390c0da0418b7667ee0df6c246c26c4be6c0618368dce5a916e8008b0db".hexStringToByteArray()

        val stateRootHash = updateSnapshot(store, 2, leafHashes2)

        val l01 = SECP256K1Keccak.treeHasher(leafHashes[0]!!, leafHashes[1]!!)
        val l23 = SECP256K1Keccak.treeHasher(leafHashes2[2]!!, leafHashes[3]!!)
        val hash00 = SECP256K1Keccak.treeHasher(l01, l23)
        val l45 = SECP256K1Keccak.treeHasher(leafHashes[4]!!, leafHashes[5]!!)
        val l67 = SECP256K1Keccak.treeHasher(leafHashes[6]!!, leafHashes[7]!!)
        val hash01 = SECP256K1Keccak.treeHasher(l45, l67)

        val l89 = SECP256K1Keccak.treeHasher(leafHashes2[8]!!, leafHashes2[9]!!)
        val hash10 = SECP256K1Keccak.treeHasher(l89, EMPTY_HASH)
        val hash11 = SECP256K1Keccak.treeHasher(EMPTY_HASH, EMPTY_HASH)
        val leftHash = SECP256K1Keccak.treeHasher(hash00, hash01)
        val rightHash = SECP256K1Keccak.treeHasher(hash10, hash11)
        val root = SECP256K1Keccak.treeHasher(leftHash, rightHash)
        assertEquals(stateRootHash.toHex(), root.toHex())
    }
}
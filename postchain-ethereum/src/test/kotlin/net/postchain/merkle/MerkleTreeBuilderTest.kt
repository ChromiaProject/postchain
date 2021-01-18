package net.postchain.merkle

import junit.framework.TestCase
import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.crypto.SECP256K1Keccak
import org.junit.Test

class MerkleTreeBuilderTest : TestCase() {

    @Test
    fun testBuildLeafs() {
        val builder = MerkleTreeBuilder(SECP256K1Keccak::treeHasher)
        val hash = "6CA54DA2C4784EA43FD88B3402DE07AE4BCED597CBB19F323B7595857A6720AE"
        val result = builder.buildLeafs(mutableListOf(hash.hexStringToByteArray()))
        assertEquals(hash, result[0].hash.toHex())
    }

    fun testBuildMerkleTree() {
        val builder = MerkleTreeBuilder(SECP256K1Keccak::treeHasher)
        val leaf1 = "c89efdaa54c0f20c7adf612882df0950f5a951637e0307cdcb4c672f298b8bc6".hexStringToByteArray()
        val leaf2 = "ad7c5bef027816a800da1736444fb58a807ef4c9603b7848673f7e3a68eb14a5".hexStringToByteArray()
        val leaf3 = "2a80e1ef1d7842f27f2e6be0972bb708b9a135c38860dbe73c27c3486c34f4de".hexStringToByteArray()
        val l12 = SECP256K1Keccak.treeHasher(leaf1, leaf2)
        val l34 = SECP256K1Keccak.treeHasher(leaf3, EMPTY_HASH)
        val expected = SECP256K1Keccak.treeHasher(l12, l34)
        val data = mutableListOf(leaf1, leaf2, leaf3)
        val leafs = builder.buildLeafs(data)
        val layer = builder.buildBottomLayer(leafs)
        val root = builder.build(layer)
        val rootHash = builder.merkleRootHash(data)
        assertEquals(root[0].digest().toHex(), expected.toHex())
        assertEquals(rootHash.toHex(), expected.toHex())
        assertEquals(rootHash.toHex(), root[0].digest().toHex())
    }
}
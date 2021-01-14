package net.postchain.merkle

import junit.framework.TestCase
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.crypto.SECP256K1Keccak
import org.junit.Test

class MerkleTreeTest : TestCase() {

    @Test
    fun testMerkleTree() {
        val leaf1 = Leaf("c89efdaa54c0f20c7adf612882df0950f5a951637e0307cdcb4c672f298b8bc6".hexStringToByteArray())
        val leaf2 = Leaf("ad7c5bef027816a800da1736444fb58a807ef4c9603b7848673f7e3a68eb14a5".hexStringToByteArray())
        val leaf3 = Leaf("2a80e1ef1d7842f27f2e6be0972bb708b9a135c38860dbe73c27c3486c34f4de".hexStringToByteArray())
        val leaf4 = Leaf("13600b294191fc92924bb3ce4b969c1e7e2bab8f4c93c3fc6d0a51733df3c060".hexStringToByteArray())

        val branch1and2 = MerkleTree(SECP256K1Keccak::treeHasher)
        branch1and2.add(leaf1, leaf2)

        val branch3and4 = MerkleTree(SECP256K1Keccak::treeHasher)
        branch3and4.add(leaf3, leaf4)

        val root = MerkleTree(SECP256K1Keccak::treeHasher)
        root.add(branch1and2, branch3and4)

        val expected = "0D498B4E4BF2C63430C21419D50B819372E1D6E0DCB411C3A973090B608E44CB"
        assertEquals(expected, root.digest().toHex())
    }
}
package net.postchain.gtv.merkle

import net.postchain.base.snapshot.SimpleDigestSystem
import net.postchain.common.data.SHA256
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvEncoder.encodeGtv
import net.postchain.gtv.GtvString
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MerkleTreeTest {

    @Test
    fun testGetMerkleProof_1() {
        val ds = SimpleDigestSystem(MessageDigest.getInstance(SHA256))
        val dict = HashMap<String, Gtv>()
        dict["el2"] = GtvByteArray("1a535d48ce851c5e5005da5a281f25ef3791449c089b8c6185be375693b8d9db1a535d48ce851c5e5005da5a281f25ef3791449c089b8c6185be375693b8d9db".hexStringToByteArray())

        val tree = MerkleTree(dict, ds)
        val pos = tree.getMerklePath("el2")
        val proof0 = tree.getMerkleProof(pos)
        val root = tree.getMerkleRoot()
        val el2 = ds.hash(ds.digest(encodeGtv(GtvString("el2"))), ds.digest(encodeGtv(dict["el2"]!!)))

        assertTrue(tree.verifyMerkleProof(proof0, pos, el2))
        assertEquals(root.toHex(), el2.toHex())
    }

    @Test
    fun testGetMerkleProof_2() {
        val ds = SimpleDigestSystem(MessageDigest.getInstance(SHA256))
        val dict = HashMap<String, Gtv>()
        dict["el2"] = GtvByteArray("044852b2a670ade5407e78fb2863c51de9fcb96542a07186fe3aeda6bb8a116d".hexStringToByteArray())
        dict["icmf"] = GtvByteArray("c89efdaa54c0f20c7adf612882df0950f5a951637e0307cdcb4c672f298b8bc6".hexStringToByteArray())

        val tree = MerkleTree(dict, ds)
        val pos0 = tree.getMerklePath("el2")
        val pos1 = tree.getMerklePath("icmf")
        val proof0 = tree.getMerkleProof(pos0)
        val proof1 = tree.getMerkleProof(pos1)

        val el2 = ds.hash(ds.digest(encodeGtv(GtvString("el2"))), ds.digest(encodeGtv(dict["el2"]!!)))
        val icmf = ds.hash(ds.digest(encodeGtv(GtvString("icmf"))), ds.digest(encodeGtv(dict["icmf"]!!)))

        assertTrue(tree.verifyMerkleProof(proof0, pos0, el2))
        assertTrue(tree.verifyMerkleProof(proof1, pos1, icmf))

        assertFalse(tree.verifyMerkleProof(proof0, pos1, el2))
        assertFalse(tree.verifyMerkleProof(proof1, pos0, icmf))
    }

    @Test
    fun testGetMerkleProof_10() {
        val ds = SimpleDigestSystem(MessageDigest.getInstance(SHA256))
        val dict = HashMap<String, Gtv>()
        for (i in 0..9) {
            val l = i.toLong()
            dict[i.toString()] = GtvByteArray(ds.digest(BigInteger.valueOf(l).toByteArray()))
        }

        val tree = MerkleTree(dict, ds)
        val pos = tree.getMerklePath("6")
        val proofs = tree.getMerkleProof(pos)

        val leaf = ds.hash(ds.digest(encodeGtv(GtvString("6"))), ds.digest(encodeGtv(GtvByteArray(ds.digest(BigInteger.valueOf(6L).toByteArray())))))

        assertTrue(tree.verifyMerkleProof(proofs, pos, leaf))
    }
}
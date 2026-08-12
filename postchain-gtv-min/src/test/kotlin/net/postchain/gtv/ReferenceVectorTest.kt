package net.postchain.gtv

import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.gtv.merkle.makeMerkleHashCalculator
import net.postchain.gtv.merkle.path.GtvPathFactory
import net.postchain.gtv.merkle.path.GtvPathSet
import net.postchain.gtv.merkle.proof.GtvMerkleProofTreeFactory
import net.postchain.gtv.merkle.proof.merkleHash
import net.postchain.gtv.merkle.proof.toGtvVirtual
import net.postchain.gtv.parse.GtvParser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every expectation in [VECTORS] was recorded from postchain-gtv 3.49.18, back when the codec was jasn1 and
 * Gson, so these tests are a byte-level conformance check rather than a restatement of current behaviour.
 */
class ReferenceVectorTest {

    private val v1 = makeMerkleHashCalculator(1)
    private val v2 = makeMerkleHashCalculator(2)

    @Test
    fun decodesReferenceBinary() {
        for (vector in VECTORS) {
            val gtv = GtvDecoder.decodeGtv(vector.der.hexStringToByteArray())
            assertEquals(vector.text, gtv.toString(), "decode ${vector.name}")
        }
    }

    @Test
    fun encodesToReferenceBinary() {
        for (vector in VECTORS) {
            val gtv = GtvDecoder.decodeGtv(vector.der.hexStringToByteArray())
            assertEquals(vector.der, GtvEncoder.encodeGtv(gtv).toHex(), "encode ${vector.name}")
        }
    }

    @Test
    fun matchesReferenceMerkleHashes() {
        for (vector in VECTORS) {
            val gtv = GtvDecoder.decodeGtv(vector.der.hexStringToByteArray())
            assertEquals(vector.merkleV1, gtv.merkleHash(v1).toHex(), "merkle v1 ${vector.name}")
            assertEquals(vector.merkleV2, gtv.merkleHash(v2).toHex(), "merkle v2 ${vector.name}")
        }
    }

    @Test
    fun matchesReferenceTextualForms() {
        for (vector in VECTORS) {
            val gtv = GtvDecoder.decodeGtv(vector.der.hexStringToByteArray())
            assertEquals(vector.text, gtv.toString(), "toString ${vector.name}")
            assertEquals(vector.short, gtv.shortString(), "shortString ${vector.name}")
            assertEquals(vector.pretty, gtv.pretty(), "pretty ${vector.name}")
        }
    }

    @Test
    fun reparsesItsOwnTextualForm() {
        for (vector in VECTORS) {
            val gtv = GtvDecoder.decodeGtv(vector.der.hexStringToByteArray())
            val text = gtv.toString()
            // Upstream's lexer mangles \uXXXX escapes (see GtvParserEscapeTest), so toString/parse is only a
            // round trip for values whose text has none. This implementation reproduces that.
            if ("\\u" in text) continue
            assertEquals(gtv, GtvParser.parse(text), "reparse ${vector.name}")
        }
    }


    @Test
    fun matchesReferenceProofs() {
        val source = GtvDecoder.decodeGtv(PROOF_SOURCE_DER.hexStringToByteArray())

        for (vector in PROOF_VECTORS) {
            val paths = GtvPathSet(setOf(GtvPathFactory.buildFromArrayOfPointers(vector.path.toTypedArray())))

            val proofV1 = source.generateProof(paths, v1)
            assertEquals(
                vector.serializedV1,
                GtvEncoder.encodeGtv(proofV1.toGtv()).toHex(),
                "proof v1 ${vector.path}",
            )
            assertEquals(vector.rootV1, proofV1.merkleHash(v1).toHex(), "proof root v1 ${vector.path}")

            val proofV2 = source.generateProof(paths, v2)
            assertEquals(
                vector.serializedV2,
                GtvEncoder.encodeGtv(proofV2.toGtv()).toHex(),
                "proof v2 ${vector.path}",
            )
            assertEquals(vector.rootV2, proofV2.merkleHash(v2).toHex(), "proof root v2 ${vector.path}")
        }
    }

    @Test
    fun deserializedProofsHashIdentically() {
        for (vector in PROOF_VECTORS) {
            val serialized = GtvDecoder.decodeGtv(vector.serializedV2.hexStringToByteArray()) as GtvArray
            val proof = GtvMerkleProofTreeFactory().deserialize(serialized)
            assertEquals(vector.rootV2, proof.merkleHash(v2).toHex(), "deserialized proof ${vector.path}")
        }
    }

    @Test
    fun proofsBecomeVirtualGtv() {
        val source = GtvDecoder.decodeGtv(PROOF_SOURCE_DER.hexStringToByteArray())
        val paths = GtvPathSet(setOf(GtvPathFactory.buildFromArrayOfPointers(arrayOf(1))))
        val virtual = source.generateProof(paths, v2).toGtvVirtual() as GtvVirtualArray

        assertEquals(4, virtual.getSize())
        assertTrue(virtual.isKeyPresent(1))
        assertEquals(GtvString("two"), virtual[1])
        assertTrue(!virtual.isKeyPresent(0))
    }
}



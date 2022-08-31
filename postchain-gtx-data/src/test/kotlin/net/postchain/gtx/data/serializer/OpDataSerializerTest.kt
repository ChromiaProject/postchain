package net.postchain.gtx.data.serializer

import com.beanit.jasn1.ber.ReverseByteArrayOutputStream
import net.postchain.common.BlockchainRid
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtx.Gtx
import net.postchain.gtx.GtxBody
import net.postchain.gtx.GtxOperation
import net.postchain.gtx.data.GTXTransactionBodyData
import net.postchain.gtx.data.GTXTransactionData
import net.postchain.gtx.data.OpData
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class OpDataSerializerTest {

    @Test
    fun opData() {
        val opData = OpData("foo", arrayOf(gtv("bar"), gtv(1)))
        val gtxOp = GtxOperation("foo", gtv("bar"), gtv(1))

        assertEquals(OpDataSerializer.serializeToGtv(opData), gtxOp.gtv())


        val encoded = ReverseByteArrayOutputStream(1000, true)
        gtxOp.asn().encode(encoded, true)

        GtvDecoder.decodeGtv(encoded.array)
        assertArrayEquals(GtvEncoder.encodeGtv(OpDataSerializer.serializeToGtv(opData)), encoded.array)
    }

    @Test
    fun txRid() {
        val calculator = GtvMerkleHashCalculator(Secp256K1CryptoSystem())
        val brid = BlockchainRid.ZERO_RID

        val oldBody = GTXTransactionBodyData(brid, arrayOf(), arrayOf())
        val newBody = GtxBody(brid, listOf(), listOf())

        assertArrayEquals(oldBody.calculateRID(calculator), newBody.calculateTxRid(calculator))

        val encoded = ReverseByteArrayOutputStream(1000, true)
        newBody.asn().encode(encoded, true)

        val oldEncoded = GtvEncoder.encodeGtv(GtxTransactionBodyDataSerializer.serializeToGtv(oldBody))

        GtvDecoder.decodeGtv(encoded.array)
        assertArrayEquals(oldEncoded, encoded.array)
    }

    @Test
    fun encode() {
        val brid = BlockchainRid.buildFromHex("ABABABABAABABABABABABABABABABABBABABABABBAABABABABABABABABABABAA")
        val newTx = Gtx(GtxBody(brid, listOf(), listOf()), listOf())
        val oldTx = GTXTransactionData(GTXTransactionBodyData(brid, arrayOf(), arrayOf()), arrayOf())

        assertArrayEquals(oldTx.serialize(), newTx.encode())
    }
}
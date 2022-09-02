package net.postchain.gtx.data.serializer

import com.beanit.jasn1.ber.ReverseByteArrayOutputStream
import net.postchain.common.BlockchainRid
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.gtx.Gtx
import net.postchain.gtx.GtxBody
import net.postchain.gtx.GtxOp
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

internal class GtxRegressionTest {

    @Test
    fun gtxOp() {
        val gtxOp = GtxOp("foo", gtv("bar"), gtv(1))

        val encoded = ReverseByteArrayOutputStream(1000, true)
        gtxOp.toRaw().encode(encoded, true)

        GtvDecoder.decodeGtv(encoded.array)
        assertArrayEquals(GtvEncoder.encodeGtv(gtxOp.toGtv()), encoded.array)
    }

    @Test
    fun gtxBody() {
        val calculator = GtvMerkleHashCalculator(Secp256K1CryptoSystem())
        val brid = BlockchainRid.ZERO_RID

        val newBody = GtxBody(brid, listOf(), listOf())

        assertArrayEquals(newBody.toGtv().merkleHash(calculator), newBody.calculateTxRid(calculator))

        val encoded = ReverseByteArrayOutputStream(1000, true)
        newBody.toRaw().encode(encoded, true)

        GtvDecoder.decodeGtv(encoded.array)
        assertArrayEquals(GtvEncoder.encodeGtv(newBody.toGtv()), encoded.array)
    }

    @Test
    fun gtx() {
        val brid = BlockchainRid.ZERO_RID
        val newTx = Gtx(GtxBody(brid, listOf(GtxOp("foo", gtv(1))), listOf()), listOf())

        GtvDecoder.decodeGtv(newTx.encode())
        assertArrayEquals(GtvEncoder.encodeGtv(newTx.toGtv()), newTx.encode())
    }
}

package net.postchain.gtx.data.serializer

import net.postchain.common.BlockchainRid
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.Gtx
import net.postchain.gtx.GtxBody
import net.postchain.gtx.GtxOp
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

internal class GtxRegressionTest {



    @Test
    fun gtx() {
        val brid = BlockchainRid.ZERO_RID
        val newTx = Gtx(GtxBody(brid, listOf(GtxOp("foo", gtv(1))), listOf()), listOf())

        GtvDecoder.decodeGtv(newTx.encode())
        assertArrayEquals(GtvEncoder.encodeGtv(newTx.toGtv()), newTx.encode())
    }
}

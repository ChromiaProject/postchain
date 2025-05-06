package net.postchain

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.base.BaseTransactionPrioritizerV2
import net.postchain.base.PRIORITIZE_QUERY_NAME_V2
import net.postchain.common.BlockchainRid
import net.postchain.configurations.GTXTestOp
import net.postchain.configurations.GTX_TEST_OP_NAME
import net.postchain.core.block.BlockQueries
import net.postchain.crypto.devtools.MockCryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtx.GTXTransaction
import net.postchain.gtx.Gtx
import net.postchain.gtx.GtxBody
import net.postchain.gtx.GtxNop
import net.postchain.gtx.GtxOp
import net.postchain.gtx.GtxTimeB
import net.postchain.gtx.data.ExtOpData
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Instant
import java.util.concurrent.CompletableFuture

class BaseTransactionPrioritizerV2Test() {

    @Test
    fun `pass compound ops to v2`() {
        val blockQueries: BlockQueries = mock {
            on { query(any(), any()) } doReturn CompletableFuture.completedStage(gtv(
                    "accountId" to gtv(ByteArray(0)),
                    "account_points" to gtv(0L),
                    "tx_cost_points" to gtv(0L),
                    "priority" to gtv("0"),
            ))
        }
        val ops = listOf(
                GtxNop(Unit, ExtOpData(GtxNop.OP_NAME, 0, arrayOf(), BlockchainRid.ZERO_RID, arrayOf(), arrayOf())),
                GTXTestOp(Unit, ExtOpData(GTX_TEST_OP_NAME, 1, arrayOf(gtv(1), gtv("")), BlockchainRid.ZERO_RID, arrayOf(), arrayOf())),
                GtxTimeB(Unit, ExtOpData(GtxTimeB.OP_NAME, 2, arrayOf(gtv(1)), BlockchainRid.ZERO_RID, arrayOf(), arrayOf())),
        )
        val tx = GTXTransaction(
                null,
                GtvNull,
                Gtx(GtxBody(BlockchainRid.ZERO_RID, ops.map { GtxOp(it.data.opName, *it.data.args) }, listOf()), listOf()),
                arrayOf(),
                arrayOf(),
                ops.toTypedArray(),
                ByteArray(32) { _ -> 0 },
                ByteArray(32) { _ -> 0 },
                MockCryptoSystem()
        )

        BaseTransactionPrioritizerV2(blockQueries).prioritize(tx, Instant.now(), Instant.now())

        argumentCaptor<Gtv> {
            verify(blockQueries).query(eq(PRIORITIZE_QUERY_NAME_V2), capture())
            assertThat(firstValue["compound_ops"]!!.asArray().map { it.asString() })
                    .isEqualTo(listOf(GtxNop.OP_NAME, GtxTimeB.OP_NAME))
        }
    }
}

package net.postchain.base.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import net.postchain.base.TxPriorityStateV1
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.common.tx.EnqueueTransactionResult
import net.postchain.common.wrap
import net.postchain.configurations.GTXTestOp
import net.postchain.configurations.GTX_TEST_OP_NAME
import net.postchain.crypto.devtools.MockCryptoSystem
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtx.GTXTransaction
import net.postchain.gtx.Gtx
import net.postchain.gtx.GtxBody
import net.postchain.gtx.GtxOp
import net.postchain.gtx.data.ExtOpData
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigDecimal.ZERO
import java.math.BigDecimal.ONE
import java.math.BigDecimal.TEN
import java.time.Clock

private val tx1 = mockGtxTransaction(1)
private val tx2 = mockGtxTransaction(2)
private val tx3 = mockGtxTransaction(3)
private val tx4 = mockGtxTransaction(4)
private val tx5 = mockGtxTransaction(5)
private val tx6 = mockGtxTransaction(6)
private val tx7 = mockGtxTransaction(7)
private val tx8 = mockGtxTransaction(8)

val TWO = BigDecimal(2)

private fun mockGtxTransaction(id: Byte) =
        GTXTransaction(
                null,
                GtvNull,
                Gtx(GtxBody(BlockchainRid.ZERO_RID, listOf(GtxOp(GTX_TEST_OP_NAME, gtv(1), gtv(""))), listOf()), listOf()),
                arrayOf(),
                arrayOf(),
                arrayOf(GTXTestOp(Unit, ExtOpData(GTX_TEST_OP_NAME, 0, arrayOf(gtv(1), gtv("")), BlockchainRid.ZERO_RID, arrayOf(), arrayOf()))),
                ByteArray(32) { _ -> id },
                ByteArray(32) { _ -> id },
                MockCryptoSystem()
        )

private fun incorrectTransaction() =
        GTXTransaction(
                null,
                GtvNull,
                Gtx(GtxBody(BlockchainRid.ZERO_RID, listOf(GtxOp(GTX_TEST_OP_NAME, gtv(1), gtv(""))), listOf()), listOf()),
                arrayOf(),
                arrayOf(),
                arrayOf(),
                ByteArray(32) { 0 },
                ByteArray(32) { 0 },
                MockCryptoSystem()
        )

class BaseTransactionQueueTest {
    private lateinit var sut: BaseTransactionQueue

    @AfterEach
    fun tearDown() {
        sut.close()
    }

    @Test
    fun `queue size is bounded`() {
        sut = BaseTransactionQueue(3, Clock.systemUTC()) { _, _, _ -> TxPriorityStateV1(ByteArray(0).wrap(), 0, 0, ONE) }
        assertThat(sut.getTransactionQueueSize()).isEqualTo(0)
        assertThat(sut.enqueue(tx1)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx2)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx3)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx4)).isEqualTo(EnqueueTransactionResult.FULL)
        assertThat(sut.getTransactionQueueSize()).isEqualTo(3)
        assertThat(sut.takeTransaction()).isNotNull()
        assertThat(sut.takeTransaction()).isNotNull()
        assertThat(sut.takeTransaction()).isNotNull()
        assertThat(sut.getTransactionQueueSize()).isEqualTo(0)
    }

    @Test
    fun `transactions with lower priority are evicted`() {
        sut = BaseTransactionQueue(3, Clock.systemUTC()) { tx, _, _ ->
            when (tx.myRID[0]) {
                1.toByte() -> TxPriorityStateV1(ByteArray(0).wrap(), 0, 0, ZERO)
                2.toByte() -> TxPriorityStateV1(ByteArray(0).wrap(), 0, 0, ONE)
                3.toByte() -> TxPriorityStateV1(ByteArray(0).wrap(), 0, 0, TWO)
                4.toByte() -> TxPriorityStateV1(ByteArray(0).wrap(), 0, 0, TEN)
                else -> TxPriorityStateV1(ByteArray(0).wrap(), 0, 0, ZERO)
            }
        }
        assertThat(sut.getTransactionQueueSize()).isEqualTo(0)
        assertThat(sut.enqueue(tx1)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx2)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx3)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx4)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.getTransactionQueueSize()).isEqualTo(3)
        assertThat(sut.takeTransaction()).isEqualTo(tx4)
        assertThat(sut.takeTransaction()).isEqualTo(tx3)
        assertThat(sut.takeTransaction()).isEqualTo(tx2)
        assertThat(sut.getTransactionQueueSize()).isEqualTo(0)
    }

    @Test
    fun `transactions with same priority are retrieved in insertion order`() {
        sut = BaseTransactionQueue(10, Clock.systemUTC()) { _, _, _ -> TxPriorityStateV1(ByteArray(0).wrap(), 0, 0, ZERO) }
        assertThat(sut.enqueue(tx1)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx2)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx3)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.takeTransaction()).isSameAs(tx1)
        assertThat(sut.takeTransaction()).isSameAs(tx2)
        assertThat(sut.takeTransaction()).isSameAs(tx3)
    }

    @Test
    fun `transactions with different priority are retrieved in priority order, and then insertion order`() {
        sut = BaseTransactionQueue(10, Clock.systemUTC()) { tx, _, _ ->
            when (tx.myRID[0]) {
                5.toByte() -> TxPriorityStateV1(ByteArray(0).wrap(), 0, 0, ONE)
                6.toByte() -> TxPriorityStateV1(ByteArray(0).wrap(), 0, 0, ONE)
                7.toByte() -> TxPriorityStateV1(ByteArray(0).wrap(), 0, 0, TEN)
                8.toByte() -> TxPriorityStateV1(ByteArray(0).wrap(), 0, 0, TEN)
                else -> TxPriorityStateV1(ByteArray(0).wrap(), 0, 0, ZERO)
            }
        }
        assertThat(sut.enqueue(tx1)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx2)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx3)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx5)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx8)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx7)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx6)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.takeTransaction()).isSameAs(tx8)
        assertThat(sut.takeTransaction()).isSameAs(tx7)
        assertThat(sut.takeTransaction()).isSameAs(tx5)
        assertThat(sut.takeTransaction()).isSameAs(tx6)
        assertThat(sut.takeTransaction()).isSameAs(tx1)
        assertThat(sut.takeTransaction()).isSameAs(tx2)
        assertThat(sut.takeTransaction()).isSameAs(tx3)
    }

    @Test
    fun `transactions can be removed`() {
        sut = BaseTransactionQueue(10, Clock.systemUTC()) { _, _, _ -> TxPriorityStateV1(ByteArray(0).wrap(), 0, 0, ZERO) }
        assertThat(sut.enqueue(tx1)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx2)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx3)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.getTransactionQueueSize()).isEqualTo(3)
        sut.removeAll(listOf(tx1, tx3))
        assertThat(sut.getTransactionQueueSize()).isEqualTo(1)
        assertThat(sut.takeTransaction()).isSameAs(tx2)
        assertThat(sut.getTransactionQueueSize()).isEqualTo(0)
    }

    @Test
    fun `incorrect transaction is rejected`() {
        sut = BaseTransactionQueue(10, Clock.systemUTC()) { _, _, _ -> TxPriorityStateV1(ByteArray(0).wrap(), 0, 0, ZERO) }
        assertThat(sut.enqueue(incorrectTransaction())).isEqualTo(EnqueueTransactionResult.INVALID)
        assertThat(sut.getTransactionQueueSize()).isEqualTo(0)
    }

    @Test
    fun `too expensive transaction is rejected`() {
        sut = BaseTransactionQueue(10, Clock.systemUTC()) { _, _, _ -> TxPriorityStateV1(ByteArray(0).wrap(), 1, 2, ZERO) }
        assertThat(sut.enqueue(tx1)).isEqualTo(EnqueueTransactionResult.FULL)
        assertThat(sut.getTransactionQueueSize()).isEqualTo(0)
    }

    @Test
    fun `transaction with lowest priority from same account is evicted when queue is full`() {
        sut = BaseTransactionQueue(3, Clock.systemUTC()) { tx, _, _ ->
            when (tx.myRID[0]) {
                1.toByte() -> TxPriorityStateV1("00".hexStringToWrappedByteArray(), 2, 1, ZERO)
                2.toByte() -> TxPriorityStateV1("01".hexStringToWrappedByteArray(), 2, 1, ONE)
                3.toByte() -> TxPriorityStateV1("01".hexStringToWrappedByteArray(), 2, 1, TWO)
                4.toByte() -> TxPriorityStateV1("01".hexStringToWrappedByteArray(), 2, 1, TEN)
                else -> TxPriorityStateV1(ByteArray(0).wrap(), 0, 0, ZERO)
            }
        }
        assertThat(sut.enqueue(tx1)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx2)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx3)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx4)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.getTransactionQueueSize()).isEqualTo(3)
        assertThat(sut.takeTransaction()).isSameAs(tx4)
        assertThat(sut.takeTransaction()).isSameAs(tx3)
        assertThat(sut.takeTransaction()).isSameAs(tx1)
        assertThat(sut.getTransactionQueueSize()).isEqualTo(0)
    }

    @Test
    fun `transaction with lowest priority is evicted when queue is full`() {
        sut = BaseTransactionQueue(3, Clock.systemUTC()) { tx, _, _ ->
            when (tx.myRID[0]) {
                1.toByte() -> TxPriorityStateV1(ByteArray(0).wrap(), 0, 0, ZERO)
                2.toByte() -> TxPriorityStateV1(ByteArray(0).wrap(), 0, 0, ONE)
                3.toByte() -> TxPriorityStateV1(ByteArray(0).wrap(), 0, 0, TWO)
                4.toByte() -> TxPriorityStateV1(ByteArray(0).wrap(), 0, 0, TEN)
                else -> TxPriorityStateV1(ByteArray(0).wrap(), 0, 0, ZERO)
            }
        }
        assertThat(sut.enqueue(tx1)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx2)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx3)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx4)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.getTransactionQueueSize()).isEqualTo(3)
        assertThat(sut.takeTransaction()).isSameAs(tx4)
        assertThat(sut.takeTransaction()).isSameAs(tx3)
        assertThat(sut.takeTransaction()).isSameAs(tx2)
        assertThat(sut.getTransactionQueueSize()).isEqualTo(0)
    }
}

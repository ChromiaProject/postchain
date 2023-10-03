package net.postchain.base.data

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import net.postchain.base.TransactionPrioritizer
import net.postchain.base.TxPriorityStateV1
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.common.tx.EnqueueTransactionResult
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
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.math.BigDecimal.ONE
import java.math.BigDecimal.TEN
import java.math.BigDecimal.ZERO
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val tx1 = mockGtxTransaction(1)
private val tx2 = mockGtxTransaction(2)
private val tx3 = mockGtxTransaction(3)
private val tx4 = mockGtxTransaction(4)
private val tx5 = mockGtxTransaction(5)
private val tx6 = mockGtxTransaction(6)
private val tx7 = mockGtxTransaction(7)
private val tx8 = mockGtxTransaction(8)

val account0 = "00".hexStringToWrappedByteArray()
val account1 = "01".hexStringToWrappedByteArray()

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
        sut = BaseTransactionQueue(3, INFINITE, INFINITE, { _, _, _ -> TxPriorityStateV1(account0, 0, 0, ONE) })
        assertThat(sut.getTransactionQueueSize()).isEqualTo(0)
        assertThat(sut.enqueue(tx1)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx2)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx3)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx4)).isEqualTo(EnqueueTransactionResult.FULL)
        assertThat(sut.getTransactionQueueSize()).isEqualTo(3)
        assertThat(sut.takeTransaction()).isNotNull()
        assertThat(sut.takeTransaction()).isNotNull()
        assertThat(sut.takeTransaction()).isNotNull()
        assertThat(sut.takeTransaction()).isNull()
        assertThat(sut.getTransactionQueueSize()).isEqualTo(0)
    }

    @Test
    fun `transactions with lower priority are evicted`() {
        sut = BaseTransactionQueue(3, INFINITE, INFINITE, { tx, _, _ ->
            when (tx.myRID[0]) {
                1.toByte() -> TxPriorityStateV1(account0, 0, 0, ZERO)
                2.toByte() -> TxPriorityStateV1(account0, 0, 0, ONE)
                3.toByte() -> TxPriorityStateV1(account0, 0, 0, TWO)
                4.toByte() -> TxPriorityStateV1(account0, 0, 0, TEN)
                else -> TxPriorityStateV1(account0, 0, 0, ZERO)
            }
        })
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
        sut = BaseTransactionQueue(10, INFINITE, INFINITE, { _, _, _ -> TxPriorityStateV1(account0, 0, 0, ZERO) })
        assertThat(sut.enqueue(tx1)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx2)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx3)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.takeTransaction()).isSameAs(tx1)
        assertThat(sut.takeTransaction()).isSameAs(tx2)
        assertThat(sut.takeTransaction()).isSameAs(tx3)
    }

    @Test
    fun `transactions with different priority are retrieved in priority order, and then insertion order`() {
        sut = BaseTransactionQueue(10, INFINITE, INFINITE, { tx, _, _ ->
            when (tx.myRID[0]) {
                5.toByte() -> TxPriorityStateV1(account0, 0, 0, ONE)
                6.toByte() -> TxPriorityStateV1(account0, 0, 0, ONE)
                7.toByte() -> TxPriorityStateV1(account0, 0, 0, TEN)
                8.toByte() -> TxPriorityStateV1(account0, 0, 0, TEN)
                else -> TxPriorityStateV1(account0, 0, 0, ZERO)
            }
        })
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
        sut = BaseTransactionQueue(10, INFINITE, INFINITE, { _, _, _ -> TxPriorityStateV1(account0, 0, 0, ZERO) })
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
        sut = BaseTransactionQueue(10, INFINITE, INFINITE, { _, _, _ -> TxPriorityStateV1(account0, 0, 0, ZERO) })
        assertThat(sut.enqueue(incorrectTransaction())).isEqualTo(EnqueueTransactionResult.INVALID)
        assertThat(sut.getTransactionQueueSize()).isEqualTo(0)
    }

    @Test
    fun `too expensive transaction is rejected`() {
        sut = BaseTransactionQueue(10, INFINITE, INFINITE, { _, _, _ -> TxPriorityStateV1(account0, 1, 2, ZERO) })
        assertThat(sut.enqueue(tx1)).isEqualTo(EnqueueTransactionResult.FULL)
        assertThat(sut.getTransactionQueueSize()).isEqualTo(0)
    }

    @Test
    fun `transaction with lowest priority from same account is evicted when queue is full`() {
        sut = BaseTransactionQueue(3, INFINITE, INFINITE, { tx, _, _ ->
            when (tx.myRID[0]) {
                1.toByte() -> TxPriorityStateV1(account0, 2, 1, ZERO)
                2.toByte() -> TxPriorityStateV1(account1, 2, 1, ONE)
                3.toByte() -> TxPriorityStateV1(account1, 2, 1, TWO)
                4.toByte() -> TxPriorityStateV1(account1, 2, 1, TEN)
                else -> TxPriorityStateV1(account0, 0, 0, ZERO)
            }
        })
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
        sut = BaseTransactionQueue(3, INFINITE, INFINITE, { tx, _, _ ->
            when (tx.myRID[0]) {
                1.toByte() -> TxPriorityStateV1(account0, 0, 0, ZERO)
                2.toByte() -> TxPriorityStateV1(account0, 0, 0, ONE)
                3.toByte() -> TxPriorityStateV1(account0, 0, 0, TWO)
                4.toByte() -> TxPriorityStateV1(account0, 0, 0, TEN)
                else -> TxPriorityStateV1(account0, 0, 0, ZERO)
            }
        })
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

    @Test
    fun `transactions can be reprioritized during recheck`() {
        val now = Instant.now()
        val clock: Clock = mock {
            on { instant() } doReturn now
        }
        val prioritizer: TransactionPrioritizer = mock {
            on { prioritize(any(), any(), any()) } doReturn
                    TxPriorityStateV1(account0, 0, 0, ZERO)
        }
        sut = BaseTransactionQueue(10, INFINITE, 100.milliseconds, prioritizer, clock)
        assertThat(sut.enqueue(tx1)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx2)).isEqualTo(EnqueueTransactionResult.OK)
        whenever(clock.instant()).doReturn(now + Duration.ofMillis(110))
        assertThat(sut.enqueue(tx3)).isEqualTo(EnqueueTransactionResult.OK)

        whenever(prioritizer.prioritize(eq(tx1), any(), any())) doReturn TxPriorityStateV1(account0, 0, 0, ZERO)
        whenever(prioritizer.prioritize(eq(tx2), any(), any())) doReturn TxPriorityStateV1(account0, 0, 0, ONE)
        whenever(prioritizer.prioritize(eq(tx3), any(), any())) doReturn TxPriorityStateV1(account0, 0, 0, ONE)
        sut.recheckPriorities()
        assertThat(sut.takeTransaction()).isSameAs(tx2)
        assertThat(sut.takeTransaction()).isSameAs(tx1)
        assertThat(sut.takeTransaction()).isSameAs(tx3)
    }

    @Test
    fun `transaction can be evicted during recheck`() {
        val now = Instant.now()
        val clock: Clock = mock {
            on { instant() } doReturn now
        }
        val prioritizer: TransactionPrioritizer = mock {
            on { prioritize(eq(tx1), any(), any()) } doReturn TxPriorityStateV1(account1, accountPoints = 3, txCostPoints = 1, ZERO)
            on { prioritize(eq(tx2), any(), any()) } doReturn TxPriorityStateV1(account1, accountPoints = 3, txCostPoints = 1, ONE)
            on { prioritize(eq(tx3), any(), any()) } doReturn TxPriorityStateV1(account1, accountPoints = 3, txCostPoints = 1, TWO)
        }
        sut = BaseTransactionQueue(10, INFINITE, 100.milliseconds, prioritizer, clock)
        assertThat(sut.enqueue(tx1)).isEqualTo(EnqueueTransactionResult.OK)
        whenever(clock.instant()).doReturn(now + Duration.ofMillis(110))
        assertThat(sut.enqueue(tx2)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx3)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.getTransactionQueueSize()).isEqualTo(3)

        whenever(prioritizer.prioritize(eq(tx1), any(), any())) doReturn TxPriorityStateV1(account1, accountPoints = 2, txCostPoints = 1, ZERO)
        sut.recheckPriorities()
        assertThat(sut.getTransactionQueueSize()).isEqualTo(2)
        assertThat(sut.takeTransaction()).isSameAs(tx3)
        assertThat(sut.takeTransaction()).isSameAs(tx2)
    }

    @Test
    fun `all transactions for account can be evicted during recheck`() {
        val now = Instant.now()
        val clock: Clock = mock {
            on { instant() } doReturn now
        }
        val prioritizer: TransactionPrioritizer = mock {
            on { prioritize(eq(tx1), any(), any()) } doReturn TxPriorityStateV1(account1, accountPoints = 2, txCostPoints = 1, ONE)
            on { prioritize(eq(tx2), any(), any()) } doReturn TxPriorityStateV1(account1, accountPoints = 2, txCostPoints = 1, TWO)
            on { prioritize(eq(tx3), any(), any()) } doReturn TxPriorityStateV1(account0, accountPoints = 2, txCostPoints = 1, ZERO)
        }
        sut = BaseTransactionQueue(10, INFINITE, 100.milliseconds, prioritizer, clock)
        assertThat(sut.enqueue(tx1)).isEqualTo(EnqueueTransactionResult.OK)
        whenever(clock.instant()).doReturn(now + Duration.ofMillis(110))
        assertThat(sut.enqueue(tx2)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx3)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.getTransactionQueueSize()).isEqualTo(3)

        whenever(prioritizer.prioritize(eq(tx1), any(), any())) doReturn TxPriorityStateV1(account1, accountPoints = 0, txCostPoints = 1, ZERO)
        sut.recheckPriorities()
        assertThat(sut.getTransactionQueueSize()).isEqualTo(1)
        assertThat(sut.takeTransaction()).isSameAs(tx3)
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    fun `returns immediately if queue has transactions`() {
        sut = BaseTransactionQueue(3, INFINITE, INFINITE, { _, _, _ -> TxPriorityStateV1(account0, 0, 0, ONE) })
        assertThat(sut.enqueue(tx1)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.getTransactionQueueSize()).isEqualTo(1)
        assertThat(sut.takeTransaction(1.minutes)).isNotNull()
        assertThat(sut.getTransactionQueueSize()).isEqualTo(0)
    }

    @Test
    @Timeout(5, unit = TimeUnit.SECONDS)
    fun `waits up to timeout if queue is empty`() {
        sut = BaseTransactionQueue(3, INFINITE, INFINITE, { _, _, _ -> TxPriorityStateV1(account0, 0, 0, ONE) })
        assertThat(sut.getTransactionQueueSize()).isEqualTo(0)
        val elapsed = measureTimeMillis {
            assertThat(sut.takeTransaction(2.seconds)).isNull()
        }
        assertThat(elapsed).isGreaterThan(2 * 1000 - 10)
    }

    @Test
    @Timeout(5, unit = TimeUnit.SECONDS)
    fun `stops waiting if transaction enters queue`() {
        sut = BaseTransactionQueue(3, INFINITE, INFINITE, { _, _, _ -> TxPriorityStateV1(account0, 0, 0, ONE) })
        assertThat(sut.getTransactionQueueSize()).isEqualTo(0)
        thread {
            Thread.sleep(1000)
            assertThat(sut.enqueue(tx1)).isEqualTo(EnqueueTransactionResult.OK)
        }
        val elapsed = measureTimeMillis {
            assertThat(sut.takeTransaction(2.seconds)).isNotNull()
        }
        assertThat(elapsed).isBetween(1000 - 10, 2 * 1000 + 10)
    }
}

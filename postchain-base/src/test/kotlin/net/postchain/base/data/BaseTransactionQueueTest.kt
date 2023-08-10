package net.postchain.base.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import net.postchain.common.tx.EnqueueTransactionResult
import net.postchain.core.Transaction
import net.postchain.core.TransactionPrioritizer
import net.postchain.core.TransactionPriority
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val tx1 = MockTransaction(1)
private val tx2 = MockTransaction(2)
private val tx3 = MockTransaction(3)
private val tx4 = MockTransaction(4)
private val tx5 = MockTransaction(5)
private val tx6 = MockTransaction(6)
private val tx7 = MockTransaction(7)
private val tx8 = MockTransaction(8)

class BaseTransactionQueueTest {
    private lateinit var sut: BaseTransactionQueue

    @AfterEach
    fun tearDown() {
        sut.close()
    }

    @Test
    fun `queue size is bounded`() {
        sut = BaseTransactionQueue(3, Duration.INFINITE) { _ -> TransactionPriority(0) }
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
    fun `transactions with same priority are retrieved in insertion order`() {
        sut = BaseTransactionQueue(10, Duration.INFINITE) { _ -> TransactionPriority(0) }
        assertThat(sut.enqueue(tx1)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx2)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx3)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.takeTransaction()).isSameAs(tx1)
        assertThat(sut.takeTransaction()).isSameAs(tx2)
        assertThat(sut.takeTransaction()).isSameAs(tx3)
    }

    @Test
    fun `transactions with different priority are retrieved in priority order, and then insertion order`() {
        sut = BaseTransactionQueue(10, Duration.INFINITE) { tx ->
            when ((tx as MockTransaction).id) {
                5.toByte() -> TransactionPriority(1)
                6.toByte() -> TransactionPriority(1)
                7.toByte() -> TransactionPriority(0)
                8.toByte() -> TransactionPriority(0)
                else -> TransactionPriority(2)
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
        sut = BaseTransactionQueue(10, Duration.INFINITE) { _ -> TransactionPriority(0) }
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
    fun `transactions can be reprioritized`() {
        sut = BaseTransactionQueue(10, 100.milliseconds, Reprioritizing())
        assertThat(sut.enqueue(tx1)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx2)).isEqualTo(EnqueueTransactionResult.OK)
        assertThat(sut.enqueue(tx3)).isEqualTo(EnqueueTransactionResult.OK)
        Thread.sleep(190)
        assertThat(sut.takeTransaction()).isSameAs(tx2)
        assertThat(sut.takeTransaction()).isSameAs(tx1)
        assertThat(sut.takeTransaction()).isSameAs(tx3)
    }

    class Reprioritizing : TransactionPrioritizer {
        companion object {
            val seq = AtomicInteger()
        }

        override fun prioritize(tx: Transaction): TransactionPriority {
            val s = seq.incrementAndGet()
            return TransactionPriority(if (s > 3 && tx === tx2) 0 else 1)
        }
    }
}

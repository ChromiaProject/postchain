// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.data

import com.google.common.util.concurrent.ThreadFactoryBuilder
import mu.KLogging
import net.postchain.common.exception.UserMistake
import net.postchain.common.tx.EnqueueTransactionResult
import net.postchain.common.tx.TransactionStatus
import net.postchain.common.types.WrappedByteArray
import net.postchain.core.Transaction
import net.postchain.core.TransactionPrioritizer
import net.postchain.core.TransactionQueue
import java.io.Closeable
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration

class ComparableTransaction(val tx: Transaction, val priority: Int, val seqNumber: Long)
    : Comparable<ComparableTransaction> {
    override fun equals(other: Any?): Boolean {
        if (other is ComparableTransaction) {
            return tx.getRID().contentEquals(other.tx.getRID())
        }
        return false
    }

    override fun hashCode(): Int {
        return tx.getRID().hashCode()
    }

    override fun compareTo(other: ComparableTransaction): Int {
        var res = priority - other.priority
        if (res == 0 && !equals(other)) res = if (seqNumber < other.seqNumber) -1 else 1
        return res
    }
}

const val MAX_REJECTED = 1000

/**
 * Transaction queue for transactions received from peers
 */
class BaseTransactionQueue(private val queueCapacity: Int, refreshInterval: Duration,
                           private val prioritizer: TransactionPrioritizer) : TransactionQueue, Closeable {

    companion object : KLogging()

    private val queue = PriorityBlockingQueue<ComparableTransaction>(queueCapacity)
    private val sequence = AtomicLong()
    private val queueMap = HashMap<WrappedByteArray, ComparableTransaction>() // transaction by RID
    private val taken = mutableListOf<ComparableTransaction>()
    private val txsToRetry: Queue<ComparableTransaction> = LinkedList()
    private val rejects = object : LinkedHashMap<WrappedByteArray, Exception?>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<WrappedByteArray, java.lang.Exception?>?): Boolean {
            return size > MAX_REJECTED
        }
    }
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
            ThreadFactoryBuilder().setNameFormat("BaseTransactionQueue").build())

    init {
        executor.scheduleAtFixedRate(::refreshPriorities,
                refreshInterval.inWholeMilliseconds, refreshInterval.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    override fun takeTransaction(): Transaction? {
        if (txsToRetry.isNotEmpty()) {
            return txsToRetry.poll().tx
        }
        val tx = queue.poll()
        return if (tx != null) {
            taken.add(tx)
            queueMap.remove(WrappedByteArray(tx.tx.getRID()))
            tx.tx
        } else null
    }

    @Synchronized
    override fun findTransaction(txRID: WrappedByteArray): Transaction? {
        return queueMap[txRID]?.tx
    }

    override fun getTransactionQueueSize(): Int {
        return queue.size
    }

    override fun enqueue(tx: Transaction): EnqueueTransactionResult {
        if (tx.isSpecial()) return EnqueueTransactionResult.INVALID

        val rid = WrappedByteArray(tx.getRID())

        synchronized(this) {
            if (queueMap.contains(rid)) {
                logger.debug { "Skipping $rid first test" }
                return EnqueueTransactionResult.DUPLICATE
            }
        }

        val transactionPriority = prioritizer.prioritize(tx)

        val comparableTx = ComparableTransaction(tx, transactionPriority.priority, sequence.getAndIncrement())
        try {
            tx.checkCorrectness()
            synchronized(this) {
                if (queueMap.contains(rid)) {
                    logger.debug { "Skipping $rid second test" }
                    return EnqueueTransactionResult.DUPLICATE
                }
                if (queue.size < queueCapacity && queue.offer(comparableTx)) {
                    logger.debug { "Enqueued tx $rid" }
                    queueMap[rid] = comparableTx
                    // If this tx was previously rejected we should clear that status now and retry it
                    rejects.remove(rid)
                    return EnqueueTransactionResult.OK
                } else {
                    logger.debug { "Skipping tx $rid, overloaded. Queue contains ${queue.size} elements" }
                    return EnqueueTransactionResult.FULL
                }
            }
        } catch (e: UserMistake) {
            logger.debug { "Tx $rid didn't pass the check: ${e.message}" }
            rejectTransaction(tx, e)
            return EnqueueTransactionResult.INVALID
        }
    }

    @Synchronized
    override fun getTransactionStatus(txHash: ByteArray): TransactionStatus {
        val rid = WrappedByteArray(txHash)
        return when {
            rid in queueMap -> TransactionStatus.WAITING
            taken.find { it.tx.getRID().contentEquals(txHash) } != null -> TransactionStatus.WAITING
            rid in rejects -> TransactionStatus.REJECTED
            else -> TransactionStatus.UNKNOWN
        }
    }

    @Synchronized
    override fun rejectTransaction(tx: Transaction, reason: Exception?) {
        taken.remove(ComparableTransaction(tx, 0, 0L))
        rejects[WrappedByteArray(tx.getRID())] = reason
    }

    @Synchronized
    override fun removeAll(transactionsToRemove: Collection<Transaction>) {
        for (tx in transactionsToRemove) {
            val ct = ComparableTransaction(tx, 0, 0L)
            queue.remove(ct)
            queueMap.remove(WrappedByteArray(tx.getRID()))
            taken.remove(ct)
            txsToRetry.remove(ct)
        }
    }

    @Synchronized
    override fun getRejectionReason(txRID: WrappedByteArray): Exception? {
        return rejects[txRID]
    }

    @Synchronized
    override fun retryAllTakenTransactions() {
        with(txsToRetry) {
            clear()
            addAll(taken)
        }
    }

    @Synchronized
    private fun refreshPriorities() {
        logger.debug("refreshPriorities")
        queueMap.forEach { (rid, ct) ->
            val transactionPriority = prioritizer.prioritize(ct.tx)
            if (transactionPriority.priority != ct.priority) {
                logger.debug { "tx $rid got new priority ${transactionPriority.priority}" }
                queue.remove(ct)
                queue.offer(ComparableTransaction(ct.tx, transactionPriority.priority, ct.seqNumber))
            }
        }
    }

    override fun close() {
        executor.shutdownNow()
        executor.awaitTermination(2, TimeUnit.SECONDS)
    }
}

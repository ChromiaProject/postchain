// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.data

import com.google.common.collect.HashMultimap
import com.google.common.util.concurrent.ThreadFactoryBuilder
import mu.KLogging
import net.postchain.base.TransactionPrioritizer
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.common.tx.EnqueueTransactionResult
import net.postchain.common.tx.TransactionStatus
import net.postchain.common.types.WrappedByteArray
import net.postchain.common.wrap
import net.postchain.core.Transaction
import net.postchain.core.TransactionQueue
import net.postchain.gtx.GTXTransaction
import java.io.Closeable
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.LinkedList
import java.util.Queue
import java.util.TreeSet
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.toJavaDuration

internal class WrappedTransaction(
        val tx: Transaction,
        val accountId: WrappedByteArray?,
        val txCostPoints: Long,
        val priority: BigDecimal,
        val seqNumber: Long,
        val enter: Instant,
        val lastRecheck: Instant)
    : Comparable<WrappedTransaction> {
    override fun equals(other: Any?): Boolean {
        if (other is WrappedTransaction) {
            return tx.getRID().contentEquals(other.tx.getRID())
        }
        return false
    }

    override fun hashCode(): Int = tx.getRID().contentHashCode()

    override fun compareTo(other: WrappedTransaction): Int {
        if (equals(other)) return 0
        val res = other.priority.compareTo(priority)
        return if (res == 0)
            (seqNumber.compareTo(other.seqNumber))
        else
            res
    }
}

const val MAX_REJECTED = 1000

/**
 * Transaction queue for transactions received from peers
 */
class BaseTransactionQueue(private val queueCapacity: Int,
                           recheckThreadInterval: Duration,
                           private val recheckTxInterval: Duration,
                           private val prioritizer: TransactionPrioritizer?,
                           private val clock: Clock = Clock.systemUTC()) : TransactionQueue, Closeable {

    companion object : KLogging()

    private val lock = ReentrantLock()
    private val nonEmpty = lock.newCondition()

    private val sequence = AtomicLong()
    private val queue = TreeSet<WrappedTransaction>()
    private val queueMap = HashMap<WrappedByteArray, WrappedTransaction>() // transaction by RID
    private val accountTxs = HashMultimap.create<WrappedByteArray, WrappedTransaction>()
    private val taken = mutableListOf<WrappedTransaction>()
    private val txsToRetry: Queue<WrappedTransaction> = LinkedList()
    private val rejects = object : LinkedHashMap<WrappedByteArray, Exception?>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<WrappedByteArray, java.lang.Exception?>?): Boolean {
            return size > MAX_REJECTED
        }
    }

    @Volatile
    private var executor: ScheduledExecutorService? = null

    init {
        if (prioritizer != null && recheckThreadInterval.isFinite()) {
            executor = Executors.newSingleThreadScheduledExecutor(
                    ThreadFactoryBuilder().setNameFormat("BaseTransactionQueue-recheckPriorities").setPriority(Thread.MIN_PRIORITY).build()).apply {
                scheduleAtFixedRate(::recheckPriorities,
                        recheckThreadInterval.inWholeMilliseconds, recheckThreadInterval.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            }
        }
    }

    override fun takeTransaction(): Transaction? = lock.withLock {
        dequeueTransaction()
    }

    override fun takeTransaction(timeout: Duration): Transaction? = lock.withLock {
        var nanosRemaining: Long = timeout.inWholeNanoseconds
        while (isEmpty()) {
            if (nanosRemaining <= 0L)
                return null
            nanosRemaining = nonEmpty.awaitNanos(nanosRemaining)
        }
        dequeueTransaction()
    }

    private fun dequeueTransaction(): Transaction? {
        if (txsToRetry.isNotEmpty()) {
            return txsToRetry.poll().tx
        }
        val tx = queue.pollFirst()
        return if (tx != null) {
            taken.add(tx)
            queueMap.remove(WrappedByteArray(tx.tx.getRID()))
            tx.accountId?.let { accountTxs.remove(it, tx) }
            tx.tx
        } else null
    }

    private fun isEmpty(): Boolean = txsToRetry.isEmpty() && queue.isEmpty()

    override fun findTransaction(txRID: WrappedByteArray): Transaction? = lock.withLock {
        queueMap[txRID]?.tx
    }

    override fun getTransactionQueueSize(): Int = lock.withLock { queue.size }

    override fun enqueue(tx: Transaction): EnqueueTransactionResult {
        val txEnter = clock.instant()

        if (tx.isSpecial()) return EnqueueTransactionResult.INVALID

        val txRid = WrappedByteArray(tx.getRID())

        lock.withLock {
            if (queueMap.contains(txRid)) {
                logger.debug { "Tx $txRid is duplicate (first test)" }
                return EnqueueTransactionResult.DUPLICATE
            }
        }

        try {
            // 1. We do is_correct() check before anything else
            tx.checkCorrectness()

            val transactionPriority = try {
                prioritizer?.prioritize(tx as GTXTransaction, txEnter, clock.instant())
            } catch (e: Exception) {
                logger.warn { "Prioritizer returned error when enqueuing $txRid: ${e.message}" }
                null
            }

            // 2. if tx_cost_points is higher than account_points, tx is immediately rejected.
            if (transactionPriority != null && transactionPriority.txCostPoints > transactionPriority.accountPoints) {
                logger.debug { "Tx $txRid costs ${transactionPriority.txCostPoints}, but only ${transactionPriority.accountPoints} available" }
                return EnqueueTransactionResult.FULL
            }

            val priority = transactionPriority?.priority ?: BigDecimal.ZERO
            val accountId = transactionPriority?.accountId
            val txCostPoints = transactionPriority?.txCostPoints ?: 0L
            val accountPoints = transactionPriority?.accountPoints ?: 0L
            val seqNumber = sequence.getAndIncrement()
            val wrappedTx = WrappedTransaction(tx, accountId, txCostPoints, priority, seqNumber, txEnter, txEnter)
            lock.withLock {
                if (queueMap.contains(txRid)) {
                    logger.debug { "Tx $txRid is duplicate (second test)" }
                    return EnqueueTransactionResult.DUPLICATE
                }
                if (queue.size < queueCapacity) {
                    enqueueTx(txRid, wrappedTx, accountId)
                    return EnqueueTransactionResult.OK
                } else {
                    logger.debug { "Queue is overloaded, contains ${queue.size} elements with capacity $queueCapacity" }

                    // 3. If queue is full, we first check if transaction can fit in the queue: if its priority is same
                    // or lower than the lowest priority in the queue, new transaction is rejected.
                    val lowestPrioTx = queue.last()
                    if (lowestPrioTx == null || priority <= lowestPrioTx.priority) {
                        logger.debug { "Tx $txRid has too low priority $priority" }
                        return EnqueueTransactionResult.FULL
                    }

                    // 4. Account-based rate limiting
                    // If there are already transactions from the account_id currently in the queue
                    val evictedTxs = accountId?.let { accountBasedRateLimiting(it, accountPoints = accountPoints, newTxCostPoints = txCostPoints) }
                            ?: 0

                    // 5. If no transactions were evicted at step 4, transaction with the lowest priority is evicted.
                    if (evictedTxs == 0) {
                        logger.debug {
                            "Evicting transaction ${lowestPrioTx.tx.getRID().toHex()} since it has lower priority ${lowestPrioTx.priority}" +
                                    " than new transaction $txRid with priority $priority"
                        }
                        queue.remove(lowestPrioTx)
                        queueMap.remove(lowestPrioTx.tx.getRID().wrap())
                        rejects[lowestPrioTx.tx.getRID().wrap()] = UserMistake("Transaction evicted due to prioritization")
                        lowestPrioTx.accountId?.let { accountTxs.remove(it, lowestPrioTx) }
                    }

                    enqueueTx(txRid, wrappedTx, accountId)
                    return EnqueueTransactionResult.OK
                }
            }
        } catch (e: UserMistake) {
            logger.debug { "Tx $txRid didn't pass the check: ${e.message}" }
            rejectTransaction(tx, e)
            return EnqueueTransactionResult.INVALID
        }
    }

    private fun enqueueTx(txRid: WrappedByteArray, wrappedTx: WrappedTransaction, accountId: WrappedByteArray?) {
        if (queue.add(wrappedTx)) {
            logger.debug { "Tx $txRid enqueued" }
            queueMap[txRid] = wrappedTx
            accountId?.let { accountTxs.put(it, wrappedTx) }

            // If this tx was previously rejected we should clear that status now and retry it
            rejects.remove(txRid)

            nonEmpty.signal()
        } else {
            throw UserMistake("Unable to enqueue tx $txRid")
        }
    }

    override fun getTransactionStatus(txRID: ByteArray): TransactionStatus = lock.withLock {
        val rid = WrappedByteArray(txRID)
        return when {
            rid in queueMap -> TransactionStatus.WAITING
            taken.find { it.tx.getRID().contentEquals(txRID) } != null -> TransactionStatus.WAITING
            rid in rejects -> TransactionStatus.REJECTED
            else -> TransactionStatus.UNKNOWN
        }
    }

    override fun rejectTransaction(tx: Transaction, reason: Exception?) {
        lock.withLock {
            taken.remove(WrappedTransaction(tx, null, 0L, BigDecimal.ZERO, 0L, Instant.EPOCH, Instant.EPOCH))
            rejects[WrappedByteArray(tx.getRID())] = reason
        }
    }

    override fun removeAll(transactionsToRemove: Collection<Transaction>) {
        lock.withLock {
            for (tx in transactionsToRemove) {
                val removedTx = queueMap.remove(WrappedByteArray(tx.getRID()))?.also { wt ->
                    wt.accountId?.let { accountTxs.remove(it, wt) }
                    queue.remove(wt)
                } ?: WrappedTransaction(tx, null, 0L, BigDecimal.ZERO, 0L, Instant.EPOCH, Instant.EPOCH)
                taken.remove(removedTx)
                txsToRetry.remove(removedTx)
            }
        }
    }

    override fun getRejectionReason(txRID: WrappedByteArray): Exception? = lock.withLock {
        rejects[txRID]
    }

    override fun retryAllTakenTransactions() {
        lock.withLock {
            with(txsToRetry) {
                clear()
                addAll(taken)
            }
            nonEmpty.signal()
        }
    }

    internal fun recheckPriorities() {
        if (prioritizer != null) {
            logger.debug { "Rechecking transactions" }
            val queueMapSnapshot = lock.withLock {
                queueMap.toList()
            }
            queueMapSnapshot.forEach { (txRid, wt) ->
                val now = clock.instant()
                if (now.isAfter(wt.lastRecheck + recheckTxInterval.toJavaDuration())) {
                    logger.debug { "Rechecking tx $txRid" }
                    try {
                        val transactionPriority = prioritizer.prioritize(wt.tx as GTXTransaction, wt.enter, now)
                        lock.withLock {
                            if (queueMap.contains(txRid)) {
                                queue.remove(wt)
                                queue.add(WrappedTransaction(wt.tx, wt.accountId, transactionPriority.txCostPoints, transactionPriority.priority, wt.seqNumber, wt.enter, now))
                                transactionPriority.accountId?.let { accountId ->
                                    accountBasedRateLimiting(
                                            accountId,
                                            accountPoints = transactionPriority.accountPoints,
                                            newTxCostPoints = 0
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn { "Prioritizer returned error when rechecking $txRid: ${e.message}" }
                    }
                }
            }
        }
    }

    // We check if sum of tx_cost_points for all transactions including the new one. If it is above account_points
    // we order transactions by priority and removes ones after the running sum exceeds account_points.
    // If a new tx was removed, it is reported as rejected. Note: more than one transaction might be evicted.
    private fun accountBasedRateLimiting(accountId: WrappedByteArray, accountPoints: Long, newTxCostPoints: Long): Int {
        var evictedTxs = 0
        val txsForAccount = accountTxs.get(accountId)
        var pointsSum = txsForAccount.sumOf { it.txCostPoints } + newTxCostPoints
        if (pointsSum > accountPoints) {
            val prioritizedTxsForAccount = txsForAccount.toSortedSet { a, b -> a.priority.compareTo(b.priority) }
            while (pointsSum > accountPoints) {
                val lowestPrioTxForAccount = prioritizedTxsForAccount.first()
                logger.debug {
                    "Evicting transaction ${lowestPrioTxForAccount.tx.getRID().toHex()} since it has lowest" +
                            " priority ${lowestPrioTxForAccount.priority} for account $accountId"
                }
                prioritizedTxsForAccount.remove(lowestPrioTxForAccount)
                queue.remove(lowestPrioTxForAccount)
                queueMap.remove(lowestPrioTxForAccount.tx.getRID().wrap())
                rejects[lowestPrioTxForAccount.tx.getRID().wrap()] = UserMistake("Transaction evicted due to account prioritization")
                evictedTxs++
                pointsSum -= lowestPrioTxForAccount.txCostPoints
            }
        }
        return evictedTxs
    }

    override fun close() {
        executor?.shutdownNow()
        executor?.awaitTermination(2, TimeUnit.SECONDS)
        executor = null
    }
}

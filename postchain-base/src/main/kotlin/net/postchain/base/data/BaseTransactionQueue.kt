// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.data

import mu.KLogging
import net.postchain.common.exception.UserMistake
import net.postchain.common.tx.EnqueueTransactionResult
import net.postchain.common.tx.TransactionStatus
import net.postchain.common.types.WrappedByteArray
import net.postchain.core.Transaction
import net.postchain.core.TransactionQueue
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.LinkedBlockingQueue

class ComparableTransaction(val tx: Transaction) {
    override fun equals(other: Any?): Boolean {
        if (other is ComparableTransaction) {
            return tx.getRID().contentEquals(other.tx.getRID())
        }
        return false
    }

    override fun hashCode(): Int {
        return tx.getRID().hashCode()
    }
}

const val MAX_REJECTED = 1000

/**
 * Transaction queue for transactions received from peers
 */
class BaseTransactionQueue(queueCapacity: Int) : TransactionQueue {

    companion object : KLogging()

    private val queue = LinkedBlockingQueue<ComparableTransaction>(queueCapacity)
    private val queueMap = HashMap<WrappedByteArray, ComparableTransaction>() // transaction by RID
    private val taken = mutableListOf<ComparableTransaction>()
    private val txsToRetry: Queue<ComparableTransaction> = LinkedList()
    private val rejects = object : LinkedHashMap<WrappedByteArray, Exception?>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<WrappedByteArray, java.lang.Exception?>?): Boolean {
            return size > MAX_REJECTED
        }
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

        val comparableTx = ComparableTransaction(tx)
        try {
            tx.checkCorrectness()
            synchronized(this) {
                if (queueMap.contains(rid)) {
                    logger.debug { "Skipping $rid second test" }
                    return EnqueueTransactionResult.DUPLICATE
                }
                if (queue.offer(comparableTx)) {
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
        taken.remove(ComparableTransaction(tx))
        rejects[WrappedByteArray(tx.getRID())] = reason
    }

    @Synchronized
    override fun removeAll(transactionsToRemove: Collection<Transaction>) {
        for (tx in transactionsToRemove) {
            val ct = ComparableTransaction(tx)
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
}
// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.data

import mu.KLogging
import net.postchain.common.data.ByteArrayKey
import net.postchain.common.exception.UserMistake
import net.postchain.common.tx.EnqueueTransactionResult
import net.postchain.common.tx.TransactionStatus
import net.postchain.core.Transaction
import net.postchain.core.TransactionQueue
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import kotlin.collections.HashMap

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
    private val queueMap = HashMap<ByteArrayKey, ComparableTransaction>() // transaction by RID
    private val taken = mutableListOf<ComparableTransaction>()
    private val txsToRetry: Queue<ComparableTransaction> = LinkedList()
    private val rejects = object : LinkedHashMap<ByteArrayKey, Exception?>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ByteArrayKey, java.lang.Exception?>?): Boolean {
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
            queueMap.remove(ByteArrayKey(tx.tx.getRID()))
            tx.tx
        } else null
    }

    @Synchronized
    override fun findTransaction(txRID: ByteArrayKey): Transaction? {
        return queueMap[txRID]?.tx
    }

    override fun getTransactionQueueSize(): Int {
        return queue.size
    }

    override fun enqueue(tx: Transaction): EnqueueTransactionResult {
        if (tx.isSpecial()) return EnqueueTransactionResult.INVALID

        val rid = ByteArrayKey(tx.getRID())
        synchronized(this) {
            if (queueMap.contains(rid)) {
                logger.debug{ "Skipping $rid first test" }
                return EnqueueTransactionResult.DUPLICATE
            }
        }

        val comparableTx = ComparableTransaction(tx)
        try {
            if (tx.isCorrect()) {
                synchronized(this) {
                    if (queueMap.contains(rid)) {
                        logger.debug{ "Skipping $rid second test" }
                        return EnqueueTransactionResult.DUPLICATE
                    }
                    if (queue.offer(comparableTx)) {
                        logger.debug{ "Enqueued tx $rid" }
                        queueMap.set(rid, comparableTx)
                        return EnqueueTransactionResult.OK
                    } else {
                        logger.debug{ "Skipping tx $rid, overloaded. Queue contains ${queue.size} elements" }
                        return EnqueueTransactionResult.FULL
                    }
                }
            } else {
                logger.debug { "Tx $rid didn't pass the check" }
                rejectTransaction(tx, null)
                return EnqueueTransactionResult.INVALID
            }

        } catch (e: UserMistake) {
            logger.debug{ "Tx $rid didn't pass the check: ${e.message}" }
            rejectTransaction(tx, e)
        }

        return EnqueueTransactionResult.UNKNOWN
    }

    @Synchronized
    override fun getTransactionStatus(txHash: ByteArray): TransactionStatus {
        val rid = ByteArrayKey(txHash)
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
        rejects[ByteArrayKey(tx.getRID())] = reason
    }

    @Synchronized
    override fun removeAll(transactionsToRemove: Collection<Transaction>) {
        for (tx in transactionsToRemove) {
            val ct = ComparableTransaction(tx)
            queue.remove(ct)
            queueMap.remove(ByteArrayKey(tx.getRID()))
            taken.remove(ct)
            txsToRetry.remove(ct)
        }
    }

    @Synchronized
    override fun getRejectionReason(txRID: ByteArrayKey): Exception? {
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
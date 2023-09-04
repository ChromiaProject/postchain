// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.common.exception.TransactionIncorrect
import net.postchain.common.exception.UserMistake
import net.postchain.common.tx.EnqueueTransactionResult
import net.postchain.common.tx.TransactionStatus
import net.postchain.common.types.WrappedByteArray

/**
 * Transactor is an individual operation which can be applied to the database
 * Transaction might consist of one or more operations
 * Transaction should be serializable, but transactor doesn't need to have a serialized
 * representation as we only care about storing of the whole Transaction
 */
interface Transactor {
    // special transactions cannot be added to a transaction queue,
    // they can only be appended directly by blockchain engine
    fun isSpecial(): Boolean

    /**
     * Check if correct.
     * @return if correct
     * @throws UserMistake if not correct */
    fun checkCorrectness() {
        @Suppress("DEPRECATION")
        if (!isCorrect()) {
            throw UserMistake("Transactor is not correct")
        }
    }

    @Deprecated(message = "Use checkCorrectness() instead to be able to get error message")
    fun isCorrect(): Boolean = throw NotImplementedError("isCorrect() is no longer supported, use checkCorrectness() instead")

    fun apply(ctx: TxEContext): Boolean
}

interface Transaction : Transactor {
    fun getRawData(): ByteArray
    fun getRID(): ByteArray  // transaction unique identifier which is used as a reference to it
    fun getHash(): ByteArray // hash of transaction content

    override fun checkCorrectness() {
        @Suppress("DEPRECATION")
        if (!isCorrect()) {
            throw TransactionIncorrect(getRID())
        }
    }
}

interface SignableTransaction : Transaction {
    val signers: Array<ByteArray>
}

interface TransactionFactory {
    fun decodeTransaction(data: ByteArray): Transaction
    fun validateTransaction(data: ByteArray)
}

interface TransactionQueue {
    fun takeTransaction(): Transaction?
    fun enqueue(tx: Transaction): EnqueueTransactionResult
    fun findTransaction(txRID: WrappedByteArray): Transaction?
    fun getTransactionStatus(txRID: ByteArray): TransactionStatus
    fun getTransactionQueueSize(): Int
    fun removeAll(transactionsToRemove: Collection<Transaction>)
    fun rejectTransaction(tx: Transaction, reason: Exception?)
    fun getRejectionReason(txRID: WrappedByteArray): Exception?
    fun retryAllTakenTransactions()
}

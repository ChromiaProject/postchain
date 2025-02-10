package net.postchain.ebft.worker

fun interface TransactionForwarder {
    fun forward(tx: net.postchain.core.Transaction)
}

package net.postchain.ebft.syncmanager.validator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.postchain.core.Shutdownable
import net.postchain.core.TransactionFactory
import net.postchain.core.TransactionQueue
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.Transaction
import net.postchain.network.CommunicationManager

class TransactionHandler(
        private val communicationManager: CommunicationManager<EbftMessage>,
        private val transactionQueue: TransactionQueue,
        private val transactionFactory: TransactionFactory
) : Shutdownable {

    private val transactionMessagesJob: Job

    init {
        transactionMessagesJob = CoroutineScope(Dispatchers.Default).launch {
            communicationManager.messages
                    .collect {
                        val (_, message) = it
                        when (message) {
                            is Transaction -> handleTransaction(message)
                        }
                    }
        }
    }

    override fun shutdown() {
        transactionMessagesJob.cancel()
    }

    /**
     * Handle transaction received from peer
     *
     * @param message message including the transaction
     */
    private fun handleTransaction(message: Transaction) {
        // TODO: reject if queue is full
        val tx = transactionFactory.decodeTransaction(message.data)
        transactionQueue.enqueue(tx)
    }
}

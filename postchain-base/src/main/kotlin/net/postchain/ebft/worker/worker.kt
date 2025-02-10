package net.postchain.ebft.worker

import net.postchain.api.rest.model.ApiStatus

interface TransactionForwarder {
    fun forward(tx: net.postchain.core.Transaction)
    fun checkStatus(tx: net.postchain.core.Transaction): ApiStatus
}

// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.data

import net.postchain.core.Transaction
import net.postchain.core.TransactionFactory


class BaseTransactionFactory : TransactionFactory {
    override fun decodeTransaction(data: ByteArray): Transaction {
        TODO("not implemented")
    }

    override fun decodeAndValidateTransaction(data: ByteArray): Transaction {
        TODO("not implemented")
    }
}
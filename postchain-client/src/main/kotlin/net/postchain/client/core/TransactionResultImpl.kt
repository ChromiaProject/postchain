// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.client.core

import net.postchain.common.tx.TransactionStatus

class TransactionResultImpl(
        override val status: TransactionStatus,
        override val httpStatusCode: Int?
) : TransactionResult

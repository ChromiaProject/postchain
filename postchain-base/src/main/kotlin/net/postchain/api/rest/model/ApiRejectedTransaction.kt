// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.model

data class ApiRejectedTransaction(val txRID: TxRid, val rejectReason: String, val rejectTimestamp: Long)

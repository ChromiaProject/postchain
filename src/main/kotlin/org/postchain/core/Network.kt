// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package org.postchain.core

interface Network {
    fun isPrimary(): Boolean
    fun broadcastTx(txData: ByteArray)
}
// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.client.core

import net.postchain.common.tx.TransactionStatus

/**
 * Acknowledge from the server. Holds the status of the TX.
 */
class TransactionAckImpl(override val status: TransactionStatus) : TransactionAck

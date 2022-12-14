// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.common.BlockchainRid

/**
 * BlockchainContext interface
 *
 * @property chainID local identifier of a blockchain within DB, like 1, 2, 3.
 * @property blockchainRID globally unique identifier of a blockchain
 * @property nodeID index of a validator within signers array. For non-validator should be NODE_ID_READONLY.
 * @property nodeRID is a block signing key, it's called subjectID in other contexts.
 */
interface BlockchainContext {
    val chainID: Long
    val blockchainRID: BlockchainRid
    val nodeID: Int
    val nodeRID: ByteArray?
}

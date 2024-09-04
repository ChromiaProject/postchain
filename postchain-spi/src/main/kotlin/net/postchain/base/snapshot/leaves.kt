// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.snapshot

import net.postchain.base.data.DatabaseAccess
import net.postchain.common.data.Hash
import net.postchain.core.BlockEContext
import net.postchain.core.TxEContext

/**
 * The name "Leaf" comes from the idea that these data objects CAN be leafs of a bigger three, where we
 * store the "nodes" somewhere else. This is not mandatory though, so we can store independent data objects here too
 * like the "icmf message events"
 */
class LeafStore {

    /**
     * Stores the event
     *
     * @param txEContext is the context (including block height and tx_iid)
     * @param prefix is what the state will be used for, for example "eif" or "icmf"
     * @param position position of event in block (e.g. 0 is for first event, etc)
     * @param hash is the hash of the data
     * @param data is the binary data
     */
    fun writeEvent(txEContext: TxEContext, prefix: String, position: Long, hash: Hash, data: ByteArray) {
        val db = DatabaseAccess.of(txEContext)
        db.insertEvent(txEContext, prefix, txEContext.height, position, hash, data)
    }

    /**
     * Stores the state
     *
     * @param blockEContext is the context (including block height)
     * @param prefix is what the state will be used for, for example "eif"
     * @param state_n is account number in state merkle tree
     * @param data is the binary data
     */
    fun writeState(blockEContext: BlockEContext, prefix: String, state_n: Long, data: ByteArray) {
        val db = DatabaseAccess.of(blockEContext)
        db.insertState(blockEContext, prefix, blockEContext.height, state_n, data)
    }

    /**
     * Delete events at and below given height
     *
     * @param blockEContext is the context (including block height)
     * @param prefix is what the state will be used for, for example "eif" or "icmf"
     * @param heightMustBeHigherThan , we will keep data above this height
     */
    fun pruneEvents(blockEContext: BlockEContext, prefix: String, heightMustBeHigherThan: Long) {
        val db = DatabaseAccess.of(blockEContext)
        db.pruneEvents(blockEContext, prefix, heightMustBeHigherThan)
    }

    /**
     * Delete all states such that state_n is between left and right and state_height <= height
     *
     * @param blockEContext is the context (including block height)
     * @param prefix is what the state will be used for, for example "eif"
     * @param left
     * @param right
     * @param heightMustBeHigherThan , we will keep data above this height
     */
    fun pruneStates(blockEContext: BlockEContext, prefix: String, left: Long, right: Long, heightMustBeHigherThan: Long) {
        val db = DatabaseAccess.of(blockEContext)
        db.pruneAccountStates(blockEContext, prefix, left, right, heightMustBeHigherThan)
    }
}
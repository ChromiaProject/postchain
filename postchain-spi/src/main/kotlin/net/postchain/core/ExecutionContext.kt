// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.gtv.Gtv
import java.sql.Connection

/**
 * Concept of a "context" is an object that knows a bit about the current state of things.
 * A "context" might know more or less things. This list starts from "stupid" and goes to "smart":
 * 1. [AppContext] - we don't know much only how to get to the DB (this is the most vague context),
 *                   Most common reason to use this context is b/c we are not working with a specific chain
 *                   at the moment.
 * 2. [ExecutionContext] - we know what blockchain we are working on (there is a similar interface called
 *                         [BlockchainContext] that also know the BC RID),
 * 3. [BlockEContext] - we also know what block we are working on,
 * 4. [TxEContext] - we also know what TX we are working on (this is the most knowledgeable context).
 */

interface AppContext {
    val conn: Connection
    fun <T> getInterface(c: Class<T>): T? {
        return null
    }
}

interface ExecutionContext : AppContext {
    val chainID: Long
}

typealias EContext = ExecutionContext

interface BlockEContext : EContext {
    val height: Long
    val blockIID: Long
    val timestamp: Long
    fun getChainDependencyHeight(chainID: Long): Long
    fun addAfterCommitHook(hook: () -> Unit)
    fun blockWasCommitted()
}

interface TxEContext : BlockEContext {
    val txIID: Long
    fun emitEvent(type: String, data: Gtv)
    // called after transaction was added to DB
    fun done()
    fun addAfterAppendHook(hook: () -> Unit)
}

// TODO: can we generalize conn? We can make it an Object, but then we have to do typecast everywhere...

/**
 * Indicates that NodeID of a consensus group member node should be determined automatically
 * the infrastructure
 */
const val NODE_ID_AUTO = -2

/**
 * Indicates that node should be configured as read-only replica which has no special role
 * in the consensus process and thus its identity does not matter.
 */
const val NODE_ID_READ_ONLY = -1

/**
 * Used when "node id" is not applicable to the blockchain configuration in question.
 */
const val NODE_ID_NA = -3

// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.base.data.BaseBlockBuilder
import net.postchain.base.data.DatabaseAccess
import net.postchain.core.AppContext
import net.postchain.core.BlockEContext
import net.postchain.core.ByteArrayKey
import net.postchain.core.EContext
import net.postchain.core.Transaction
import net.postchain.core.TxEContext
import net.postchain.gtv.Gtv
import java.sql.Connection

class ConfirmationProofMaterial(
    val txHash: ByteArrayKey,
    val txHashes: Array<ByteArrayKey>,
    val header: ByteArray,
    val witness: ByteArray
)

open class BaseAppContext(
    override val conn: Connection,
    private val dbAccess: DatabaseAccess
) : AppContext {

    override fun <T> getInterface(c: Class<T>): T? {
        return if (c == DatabaseAccess::class.java) {
            dbAccess as T?
        } else null
    }
}

open class BaseEContext(
    override val conn: Connection,
    override val chainID: Long,
    private val dbAccess: DatabaseAccess
) : EContext {

    override fun <T> getInterface(c: Class<T>): T? {
        return if (c == DatabaseAccess::class.java) {
            dbAccess as T?
        } else null
    }
}

interface TxEventSink {
    fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv)
}

open class BaseBlockEContext(
    val ectx: EContext,
    override val height: Long,
    override val blockIID: Long,
    override val timestamp: Long,
    val dependencyHeightMap: Map<Long, Long>,
    val txEventSink: TxEventSink
) : EContext by ectx, BlockEContext {


    override fun <T> getInterface(c: Class<T>): T? {
        return if (c == TxEventSink::class.java) {
            txEventSink as T?
        } else ectx.getInterface(c)
    }

    /**
     * @param chainID is the blockchain dependency we want to look at
     * @return the required height of the blockchain (specificied by the chainID param)
     *         or null if there is no such dependency.
     *         (Note that Height = -1 is a dependency without any blocks, which is allowed)
     */
    override fun getChainDependencyHeight(chainID: Long): Long {
        return dependencyHeightMap[chainID]
            ?: throw IllegalArgumentException("The blockchain with chain ID: $chainID is not a dependency")
    }
}

open class BaseTxEContext(
    val bectx: BlockEContext,
    override val txIID: Long,
    val tx: Transaction
) : BlockEContext by bectx, TxEContext {
    val events = mutableListOf<Pair<String, Gtv>>()
    val eventSink = bectx.getInterface(TxEventSink::class.java)!!

    override fun emitEvent(type: String, data: Gtv) {
        if (type.startsWith("!")) {
            eventSink.processEmittedEvent(this, type, data)
        } else {
            events.add(Pair(type, data))
        }
    }

    override fun done() {
        if (events.isNotEmpty()) {
            for (e in events) {
                eventSink.processEmittedEvent(this, e.first, e.second)
            }
        }
    }
}

/**
 * NOTE: Remember that the BBB Extension is just a part of many extension interfaces working together.
 * (examples: Spec TX Ext and Sync Ext).
 * To see how it all goes together, see: doc/extension_classes.graphml
 */
interface BaseBlockBuilderExtension {
    fun init(blockEContext: BlockEContext, baseBB: BaseBlockBuilder)
    fun finalize(): Map<String, Gtv>
}
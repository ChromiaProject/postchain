// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.core.Shutdownable
import net.postchain.core.Transactor
import net.postchain.gtv.Gtv
import net.postchain.gtx.data.ExtOpData
import net.postchain.gtx.special.GTXSpecialTxExtension

const val NON_STRICT_QUERY_ARGUMENT = "~non-strict"

/**
 * The GTX Module is the basis of a "Dapp".
 */
interface GTXModule : Shutdownable {
    fun makeTransactor(opData: ExtOpData): Transactor
    fun getOperations(): Set<String>
    fun getQueries(): Set<String>
    fun query(ctxt: EContext, name: String, args: Gtv): Gtv
    fun initializeDB(ctx: EContext)
    fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension>
    fun getSpecialTxExtensions(): List<GTXSpecialTxExtension>

    override fun shutdown() {}
}

interface PostchainContextAware {
    fun initializeContext(configuration: BlockchainConfiguration, postchainContext: PostchainContext)
}

fun interface TransactorMaker {
    fun makeTransactor(opData: ExtOpData): Transactor
}

interface OperationWrapper {
    fun getWrappingOperations(): Set<String>
    fun injectDelegateTransactorMaker(transactorMaker: TransactorMaker)
}

interface GTXModuleFactory {
    fun makeModule(config: Gtv, blockchainRID: BlockchainRid): GTXModule
}

/**
 * This template/dummy class provides simple implementations for everything except "initializeDB()"
 * (It's up to subclasses to override whatever they need)
 */
abstract class SimpleGTXModule<ConfT>(
        val conf: ConfT,
        val opmap: Map<String, (ConfT, ExtOpData) -> Transactor>,
        val querymap: Map<String, (ConfT, EContext, Gtv) -> Gtv>
) : GTXModule {

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> {
        return listOf()
    }

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> {
        return listOf()
    }

    override fun makeTransactor(opData: ExtOpData): Transactor {
        if (opData.opName in opmap) {
            return opmap[opData.opName]!!(conf, opData)
        } else {
            throw UnknownOperation(opData.opName)
        }
    }

    override fun getOperations(): Set<String> {
        return opmap.keys
    }

    override fun getQueries(): Set<String> {
        return querymap.keys
    }

    override fun query(ctxt: EContext, name: String, args: Gtv): Gtv {
        if (name in querymap) {
            return querymap[name]!!(conf, ctxt, args)
        } else throw UnknownQuery(name)
    }
}

class CompositeGTXModule(val modules: Array<GTXModule>, val allowOverrides: Boolean) : GTXModule, PostchainContextAware {

    lateinit var wrappingOpMap: Map<String, GTXModule>
    lateinit var opmap: Map<String, GTXModule>
    lateinit var qmap: Map<String, GTXModule>
    lateinit var ops: Set<String>
    lateinit var _queries: Set<String>
    lateinit var _specialTxExtensions: List<GTXSpecialTxExtension>

    companion object : KLogging()

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> {
        val l = mutableListOf<BaseBlockBuilderExtension>()
        for (m in modules) {
            l.addAll(m.makeBlockBuilderExtensions())
        }
        return l
    }

    override fun getSpecialTxExtensions() = _specialTxExtensions

    override fun makeTransactor(opData: ExtOpData): Transactor {
        if (opData.opName in ops) {
            return (wrappingOpMap[opData.opName] ?: opmap[opData.opName])!!.makeTransactor(opData)
        } else {
            throw UnknownOperation(opData.opName)
        }
    }

    override fun getOperations(): Set<String> {
        return ops
    }

    override fun getQueries(): Set<String> {
        return _queries
    }

    override fun query(ctxt: EContext, name: String, args: Gtv): Gtv {
        if (name in qmap) {
            return qmap[name]!!.query(ctxt, name, args)
        } else {
            throw UnknownQuery(name)
        }
    }

    override fun initializeDB(ctx: EContext) {
        for (module in modules) {
            logger.debug { "Initialize DB for module: $module" } // TODO: Should probably write the module name here
            module.initializeDB(ctx)
        }
        val _wrappingOpMap = mutableMapOf<String, GTXModule>()
        val _opmap = mutableMapOf<String, GTXModule>()
        val _qmap = mutableMapOf<String, GTXModule>()
        val _stxs = mutableListOf<GTXSpecialTxExtension>()
        for (m in modules) {
            for (op in m.getOperations()) {
                if (m is OperationWrapper && op in m.getWrappingOperations()) {
                    if (!allowOverrides && op in _wrappingOpMap) throw UserMistake("Duplicated wrapping operation")
                    _wrappingOpMap[op] = m
                } else {
                    if (!allowOverrides && op in _opmap) throw UserMistake("Duplicated operation")
                    _opmap[op] = m
                }
            }
            for (q in m.getQueries()) {
                if (!allowOverrides && q in _qmap) throw UserMistake("Duplicated query")
                _qmap[q] = m
            }
            _stxs.addAll(m.getSpecialTxExtensions())
            if (m is OperationWrapper) m.injectDelegateTransactorMaker(TransactorMaker { opData ->
                if (opData.opName in ops) {
                    opmap[opData.opName]!!.makeTransactor(opData)
                } else {
                    throw UnknownOperation(opData.opName)
                }
            })
        }
        wrappingOpMap = _wrappingOpMap.toMap()
        opmap = _opmap.toMap()
        qmap = _qmap.toMap()
        ops = wrappingOpMap.keys + opmap.keys
        _queries = qmap.keys
        _specialTxExtensions = _stxs.toList()
    }

    override fun initializeContext(configuration: BlockchainConfiguration, postchainContext: PostchainContext) {
        modules.filterIsInstance(PostchainContextAware::class.java).forEach { it.initializeContext(configuration, postchainContext) }
    }

    override fun shutdown() {
        for (module in modules) {
            module.shutdown()
        }
    }
}

class UnknownQuery(val name: String) : UserMistake("Unknown query: $name")
class UnknownOperation(val name: String) : UserMistake("Unknown operation: $name")

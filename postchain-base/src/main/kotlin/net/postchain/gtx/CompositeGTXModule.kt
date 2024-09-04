package net.postchain.gtx

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.core.Transactor
import net.postchain.gtv.Gtv
import net.postchain.gtx.data.ExtOpData
import net.postchain.gtx.special.GTXSpecialTxExtension

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
            logger.debug { "Initialize DB for module: $module" }
            module.initializeDB(ctx)
        }
        val _wrappingOpMap = mutableMapOf<String, GTXModule>()
        val _opmap = mutableMapOf<String, GTXModule>()
        val _qmap = mutableMapOf<String, GTXModule>()
        val _stxs = mutableListOf<GTXSpecialTxExtension>()
        for (m in modules) {
            for (op in m.getOperations()) {
                if (m is OperationWrapper && op in m.getWrappingOperations()) {
                    if (!allowOverrides && op in _wrappingOpMap) throw UserMistake("Duplicated wrapping operation: $op")
                    _wrappingOpMap[op] = m
                } else {
                    if (!allowOverrides && op in _opmap) throw UserMistake("Duplicated operation: $op")
                    _opmap[op] = m
                }
            }
            for (q in m.getQueries()) {
                if (!allowOverrides && q in _qmap) throw UserMistake("Duplicated query: $q")
                _qmap[q] = m
            }
            _stxs.addAll(m.getSpecialTxExtensions())
            if (m is OperationWrapper) m.injectDelegateTransactorMaker(TransactorMaker { opData ->
                if (opData.opName in opmap.keys) {
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
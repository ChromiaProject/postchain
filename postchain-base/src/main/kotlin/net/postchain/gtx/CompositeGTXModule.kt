package net.postchain.gtx

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.snapshot.LeafStore
import net.postchain.base.snapshot.RootSnapshotBlockBuilderExtension
import net.postchain.base.snapshot.SimpleDigestSystem
import net.postchain.base.snapshot.SnapshotPageStore
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.core.Transactor
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.merkleHash
import net.postchain.gtx.data.ExtOpData
import net.postchain.gtx.special.GTXSpecialTxExtension
import java.security.MessageDigest
import java.util.TreeMap

class CompositeGTXModule(val modules: Array<GTXModule>, val allowOverrides: Boolean) : GTXModule, PostchainContextAware {

    lateinit var wrappingOpMap: Map<String, GTXModule>
    lateinit var opmap: Map<String, GTXModule>
    lateinit var qmap: Map<String, GTXModule>
    lateinit var ops: Set<String>
    lateinit var _queries: Set<String>
    lateinit var _specialTxExtensions: List<GTXSpecialTxExtension>
    var snapshotsEnabled = false

    companion object : KLogging()

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> {
        val l = mutableListOf<BaseBlockBuilderExtension>()
        for (m in modules) {
            l.addAll(m.makeBlockBuilderExtensions())
        }
        if (snapshotsEnabled) l.add(RootSnapshotBlockBuilderExtension())
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

        DatabaseAccess.of(ctx).apply {
            createPageTable(ctx,"${SNAPSHOT_TABLE_PREFIX}_root_snapshot")
            modules.filterIsInstance<SnapshotAware>().forEach {
                val contextId = getOrGenerateSnapshotContextId(ctx, it::class.java.canonicalName)
                createPageTable(ctx,"${SNAPSHOT_TABLE_PREFIX}_${contextId}_snapshot")
                createStateLeafTable(ctx,"${SNAPSHOT_TABLE_PREFIX}_$contextId")
            }
        }
    }

    override fun initializeContext(configuration: BlockchainConfiguration, postchainContext: PostchainContext) {
        modules.filterIsInstance<PostchainContextAware>()
                .forEach { it.initializeContext(configuration, postchainContext) }

        // Initialize snapshot contexts
        snapshotsEnabled = BlockchainConfigurationData.isSnapshotEnabled(configuration.rawConfig)
        if (snapshotsEnabled) {
            modules.filterIsInstance<SnapshotAware>()
                    .forEach { module -> module.initializeSnapshotContext({ ctx, datumId, datum, isPermanent ->
                        // TODO: Might be better to store hash of canonical name (makes debugging harder though)?
                        val contextId = DatabaseAccess.of(ctx).getSnapshotContextId(ctx, module::class.java.canonicalName)
                        val snapshotPageStore = SnapshotPageStore(
                                ctx, 2, 0, SimpleDigestSystem(MessageDigest.getInstance("SHA-256")), "${SNAPSHOT_TABLE_PREFIX}_$contextId"
                        )
                        snapshotPageStore.updateSnapshot(ctx.height, TreeMap(mapOf(datumId to datum.merkleHash(configuration.merkleHashCalculator))), 2)
                        if (!isPermanent) {
                            LeafStore().writeState(ctx, "${SNAPSHOT_TABLE_PREFIX}_$contextId", datumId, GtvEncoder.encodeGtv(datum))
                        }
                    })}
        }
    }

    override fun shutdown() {
        for (module in modules) {
            module.shutdown()
        }
    }
}
package net.postchain.gtx

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.api.rest.model.ApiMetadata
import net.postchain.api.rest.model.OperationMetadata
import net.postchain.api.rest.model.QueryMetadata
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DatumInfo
import net.postchain.base.snapshot.RootSnapshotBlockBuilderExtension
import net.postchain.base.snapshot.SnapshotBlockchainConfigurationData
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.core.Transactor
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorBase
import net.postchain.gtv.merkleHash
import net.postchain.gtx.data.ExtOpData
import net.postchain.gtx.special.GTXSpecialTxExtension

class CompositeGTXModule(
        val modules: Array<GTXModule>,
        val allowOverrides: Boolean,
        val snapshotsEnabled: Boolean,
        val snapshotConfig: SnapshotBlockchainConfigurationData,
        val merkleHashCalculator: GtvMerkleHashCalculatorBase
) : GTXModule, PostchainContextAware, MetadataProvider {

    lateinit var wrappingOpMap: Map<String, GTXModule>
    lateinit var opmap: Map<String, GTXModule>
    lateinit var qmap: Map<String, GTXModule>
    lateinit var ops: Set<String>
    lateinit var _queries: Set<String>
    val _specialTxExtensions: List<GTXSpecialTxExtension> by lazy {
        modules.flatMap { it.getSpecialTxExtensions() }
    }
    lateinit var _metadata: GTXModuleMetadata
    lateinit var _compositeMetadata: ApiMetadata

    private val moduleContextIds = mutableMapOf<String, Long>()

    companion object : KLogging()

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> {
        val l = mutableListOf<BaseBlockBuilderExtension>()
        for (m in modules) {
            l.addAll(m.makeBlockBuilderExtensions())
        }
        if (snapshotsEnabled) l.add(RootSnapshotBlockBuilderExtension(snapshotConfig.snapshotInterval,
                snapshotConfig.levelsPerPage, snapshotConfig.snapshotsToKeep))
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
            logger.debug { "Initialize DB for module: ${module.javaClass.name}" }
            module.initializeDB(ctx)
        }
        val _wrappingOpMap = mutableMapOf<String, GTXModule>()
        val _opmap = mutableMapOf<String, GTXModule>()
        val _qmap = mutableMapOf<String, GTXModule>()
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
            if (m is OperationWrapper) m.injectDelegateTransactorMaker { opData ->
                if (opData.opName in opmap.keys) {
                    opmap[opData.opName]!!.makeTransactor(opData)
                } else {
                    throw UnknownOperation(opData.opName)
                }
            }
        }
        wrappingOpMap = _wrappingOpMap.toMap()
        opmap = _opmap.toMap()
        qmap = _qmap.toMap()
        ops = wrappingOpMap.keys + opmap.keys
        _queries = qmap.keys

        val metadataCollection = modules.filterIsInstance<MetadataProvider>().map { it.javaClass.canonicalName to it.getMetadata() }
        _metadata = GTXModuleMetadata(
                operations = metadataCollection.map { (_, metadata) -> metadata.operations }.fold(mapOf()) { acc, map -> acc + map },
                queries = metadataCollection.map { (_, metadata) -> metadata.queries }.fold(mapOf()) { acc, map -> acc + map }
        )
        _compositeMetadata = ApiMetadata(
                operations = metadataCollection.map { (moduleName, metadata) ->
                    metadata.operations.mapValues { OperationMetadata(moduleName, it.value.args) }
                }.fold(mapOf()) { acc, map -> acc + map },
                queries = metadataCollection.map { (moduleName, metadata) ->
                    metadata.queries.mapValues { QueryMetadata(moduleName, it.value.args, it.value.returnType) }
                }.fold(mapOf()) { acc, map -> acc + map }
        )

        if (snapshotsEnabled) {
            DatabaseAccess.of(ctx).apply {
                createPageTable(ctx,"${SNAPSHOT_TABLE_PREFIX}_root_snapshot")
                modules.filterIsInstance<SnapshotAware>().forEach {
                    val (contextId, created) = getOrGenerateSnapshotContextId(ctx, it::class.java.canonicalName)
                    moduleContextIds[it::class.java.canonicalName] = contextId
                    createPageTable(ctx,"${SNAPSHOT_TABLE_PREFIX}_${contextId}_snapshot")
                    createStateLeafTable(ctx,"${SNAPSHOT_TABLE_PREFIX}_$contextId")
                    if (created) {
                        it.getInitialDatums(ctx).forEach { datum ->
                            updateDatum(ctx, merkleHashCalculator, contextId, datum.id,
                                    datum.data, datum.isPermanent)
                        }
                    }
                }
            }
        }
    }

    override fun initializeContext(configuration: BlockchainConfiguration, postchainContext: PostchainContext, ctx: EContext) {
        modules.filterIsInstance<PostchainContextAware>()
                .forEach { it.initializeContext(configuration, postchainContext, ctx) }

        // Initialize snapshot contexts
        if (snapshotsEnabled) {
            modules.filterIsInstance<SnapshotAware>()
                    .forEach { module ->
                        val contextId = moduleContextIds[module::class.java.canonicalName]
                                ?: throw ProgrammerMistake("Module ${module::class.java.canonicalName} is snapshot aware but has no cached context id")

                        module.initializeSnapshotContext{ ctx, datumId, datum, isPermanent ->
                            DatabaseAccess.of(ctx).apply {
                                updateDatum(ctx, configuration.merkleHashCalculator, contextId, datumId, datum, isPermanent)
                            }
                        }
                    }
        }
    }

    private fun updateDatum(ctx: EContext, merkleHashCalculator: GtvMerkleHashCalculatorBase, contextId: Long,
                            datumId: Long, datum: Gtv, isPermanent: Boolean) {
        DatabaseAccess.of(ctx).apply {
            insertUpdatedDatum(ctx, contextId, DatumInfo(
                    datumId,
                    datum.merkleHash(merkleHashCalculator),
                    if (isPermanent) null else GtvEncoder.encodeGtv(datum)
            ))
        }
    }

    override fun shutdown() {
        for (module in modules) {
            module.shutdown()
        }
    }

    override fun getMetadata(): GTXModuleMetadata = _metadata

    fun getCompositeMetadata(): ApiMetadata = _compositeMetadata
}

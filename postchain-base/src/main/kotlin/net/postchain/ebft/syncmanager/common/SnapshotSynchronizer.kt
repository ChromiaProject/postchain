package net.postchain.ebft.syncmanager.common

import mu.KLogging
import net.postchain.base.BaseBlockWitness
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.configuration.snapshot
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DatumInfo
import net.postchain.base.data.SnapshotSyncContextState
import net.postchain.base.data.SnapshotSyncState
import net.postchain.base.extension.CONFIG_HASH_EXTRA_HEADER
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.snapshot.RangeProof
import net.postchain.base.snapshot.RootSnapshotBlockBuilder
import net.postchain.base.snapshot.SNAPSHOT_ROOT_EXTRA_HEADER
import net.postchain.base.snapshot.SimpleDigestSystem
import net.postchain.base.snapshot.SnapshotDatum
import net.postchain.base.snapshot.VerifyRangeProof
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.data.Hash
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.toHex
import net.postchain.concurrent.util.get
import net.postchain.core.BlockRid
import net.postchain.core.EContext
import net.postchain.core.NodeRid
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PrivKey
import net.postchain.crypto.PubKey
import net.postchain.ebft.BlockWriter
import net.postchain.ebft.message.BlockHeader
import net.postchain.ebft.message.EbftVersion
import net.postchain.ebft.message.GetBlockAtHeight
import net.postchain.ebft.message.GetBlockHeaderAndBlock
import net.postchain.ebft.message.GetBlockRange
import net.postchain.ebft.message.GetBlockSignature
import net.postchain.ebft.message.GetLatestSnapshotBlock
import net.postchain.ebft.message.GetSnapshotData
import net.postchain.ebft.message.SnapshotBlockHeader
import net.postchain.ebft.message.SnapshotBlockHeaderContextData
import net.postchain.ebft.message.SnapshotData
import net.postchain.ebft.message.SnapshotRangeProof
import net.postchain.ebft.syncmanager.configuration.RateLimitConfiguration
import net.postchain.ebft.worker.WorkerContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.merkleHash
import net.postchain.gtx.SnapshotAware
import net.postchain.managed.ManagedBlockchainConfigurationProvider
import org.apache.commons.io.FileUtils
import java.lang.Thread.sleep
import java.time.Clock
import java.util.TreeMap
import kotlin.time.DurationUnit
import kotlin.time.measureTime

class SnapshotSynchronizer(
        workerContext: WorkerContext,
        private val blockDatabase: BlockWriter,
        val params: SyncParameters,
        val peerStatuses: PeerStatuses,
        val isProcessRunning: () -> Boolean,
        rateLimitConfiguration: RateLimitConfiguration,
        private val verifyRangeProof: VerifyRangeProof = VerifyRangeProof(SimpleDigestSystem(workerContext.appConfig.cryptoSystem)),
        private val clock: Clock = Clock.systemUTC(),
) : AbstractSynchronizer(workerContext, rateLimitConfiguration) {

    companion object : KLogging() {
        const val SNAPSHOT_CONFIG_FETCH_RETRY_INTERVAL_MS = 10_000L
    }

    private val receivedLatestSnapshotHeight = mutableMapOf<NodeRid, SnapshotBlockHeader>()
    private val contextSyncRequests = mutableMapOf<Long, SnapshotContextState>() // State of progress per context, each context is removed on completion
    private val lastStoredSnapshotDataTime = mutableMapOf<Long, Long>() // <context ID, time in milliseconds> - tracks "waiting time" between writing datums to storage.

    private lateinit var syncState: SnapshotSyncState
    private val contextStates = mutableMapOf<Long, SnapshotSyncContextState>() // <context ID, state> - contains initial states at startup and context root hash

    private val snapshotModuleByContextMap: Map<Long, SnapshotAware> by lazy {
        val configuration = workerContext.engine.getConfiguration()
        val snapshotModules = configuration.getSnapshotAwareModules()
        withReadConnection(workerContext.engine.blockBuilderStorage, configuration.chainID) { ctx ->
            val dba = DatabaseAccess.of(ctx)
            dba.getSnapshotModuleContextIds(ctx).associateWith {
                val moduleName = dba.getSnapshotContextModule(ctx, it)
                snapshotModules.find { module -> module::class.java.canonicalName == moduleName }
                        ?: throw ProgrammerMistake("No module found for snapshot context id $it")
            }
        }
    }

    /** This is used for testing, to verify stages */
    private val snapshotSyncEvents = SnapshotSyncEvents()

    fun trySnapshotSync() {
        if (shouldDoSnapshotSync()) {
            // Start with blocks, we have a blockdb that simply saves the block without applying txs (after checking signature)
            // TODO: Probably good with some extra parallelism here?
            val fastSynchronizer = FastSynchronizer(
                    workerContext,
                    blockDatabase,
                    params,
                    PeerStatuses(params.syncPeerParameters),
                    { isProcessRunning() },
                    rateLimitConfiguration
            )
            fastSynchronizer.syncUntil { !isProcessRunning() }
            syncSnapshotUntil()
            params.syncToExactHeight = -1
        }
    }

    fun getContextStates(): List<SnapshotSyncContextState> {
        return contextStates.values.toList()
    }

    private fun shouldDoSnapshotSync(): Boolean {

        if (loadOngoingSyncState()) {
            logger.info("Continuing snapshot sync for height ${syncState.height} with context offsets: ${contextStates.values.map { it.contextId to it.datumIdOffset }}")
            snapshotSyncEvents.add(SnapshotSyncEvent.WILL_CONTINUING_SYNC)
            return true
        } else if (blockQueries.getLastBlockHeight().get() > 0) {
            logger.info("Last block height for this node is greater than 0. Not syncing snapshot")
            snapshotSyncEvents.add(SnapshotSyncEvent.NO_SYNC_DUE_TO_HEIGHT_NOT_0)
            return false
        }

        var lastRequestForSnapshotHeight = 0L
        var numberOfTries = 0
        while (isProcessRunning()) {
            val peersLeft = configuredPeers - receivedLatestSnapshotHeight.keys
            if (peersLeft.isEmpty()) break

            if (clock.millis() - lastRequestForSnapshotHeight >= params.jobTimeout) {
                if (numberOfTries > 3) break

                peersLeft.forEach {
                    communicationManager.sendPacket(GetLatestSnapshotBlock(), it)
                    logger.info("Requested latest snapshot block from peer $it")
                }
                lastRequestForSnapshotHeight = clock.millis()
                numberOfTries++
            }
            processMessages(true)
            sleep(params.loopInterval)
        }
        // We need to validate the witness of these headers
        val snapshotCandidates = receivedLatestSnapshotHeight
                .filter { (_, blockHeader) -> blockHeader.header.isNotEmpty() }
                .map { (peerId, blockHeader) -> peerId to blockHeader }
                .sortedByDescending { (_, blockHeader) -> BlockHeaderData.fromBinary(blockHeader.header).getHeight() }

        if (snapshotCandidates.isEmpty()) {
            logger.debug { "No snapshot candidates found. Not syncing snapshot." }
            return false
        }

        // We try all but unless peers are malicious or we are lacking config it should be fine
        for ((peerId, candidate) in snapshotCandidates) {
            val candidateHeader = BlockHeaderData.fromBinary(candidate.header)
            val candidateHeaderConfig = candidateHeader.getExtra()[CONFIG_HASH_EXTRA_HEADER]?.asByteArray()
            val candidateHeaderRid = BlockRid(candidateHeader.toGtv().merkleHash(blockchainConfiguration.merkleHashCalculator))
            val candidateHeaderHeight = candidateHeader.getHeight()

            val (validator, witnessBuilder) = if (blockchainConfiguration.configHash.contentEquals(candidateHeaderConfig)) {
                val validator = blockchainConfiguration.getBlockHeaderValidator() // We have the snapshot height config now!
                validator to validator.createWitnessBuilderWithoutOwnSignature(candidateHeaderRid)
            } else {
                val bcConfigProvider = workerContext.blockchainConfigurationProvider

                var snapshotHeightConfigData: BlockchainConfigurationData? = null
                while (snapshotHeightConfigData == null) {
                    withReadConnection(workerContext.engine.blockBuilderStorage, blockchainConfiguration.chainID) { ctx ->
                        bcConfigProvider.getHistoricConfiguration(ctx, blockchainConfiguration.chainID, candidateHeaderHeight)
                    }?.let {
                        val snapshotAppliedConfigData = BlockchainConfigurationData.fromRaw(it)
                        if (candidateHeaderConfig != null && !snapshotAppliedConfigData.configHash.contentEquals(candidateHeaderConfig)) {
                            // There is a possibility that the snapshot header configuration is pending
                            if (bcConfigProvider is ManagedBlockchainConfigurationProvider) {
                                val matchingPendingConfig = getConfigIfPending(bcConfigProvider, candidateHeaderHeight, candidateHeaderConfig)
                                if (matchingPendingConfig != null) {
                                    snapshotHeightConfigData = BlockchainConfigurationData.fromRaw(matchingPendingConfig.fullConfig)
                                }
                            }
                        } else snapshotHeightConfigData = snapshotAppliedConfigData
                    }

                    if (snapshotHeightConfigData == null) {
                        logger.warn("Unable to find config with hash ${candidateHeaderConfig?.toHex()} at height $candidateHeaderHeight. Retrying in $SNAPSHOT_CONFIG_FETCH_RETRY_INTERVAL_MS ms...")
                        val endTime = clock.millis() + SNAPSHOT_CONFIG_FETCH_RETRY_INTERVAL_MS
                        while (clock.millis() < endTime) {
                            sleep(100)
                            if (!isProcessRunning()) return false

                            processMessages(false) // Ensure we continue to process messages
                        }
                    }
                }

                val myKeyPair = KeyPair(PubKey(workerContext.appConfig.pubKeyByteArray), PrivKey(workerContext.appConfig.privKeyByteArray))
                val validator = baseBlockWitnessProviderProvider(
                        workerContext.appConfig.cryptoSystem,
                        workerContext.appConfig.cryptoSystem.buildSigMaker(myKeyPair),
                        snapshotHeightConfigData.signers.toTypedArray()
                )
                validator to validator.createWitnessBuilderWithoutOwnSignature(candidateHeaderRid)
            }

            try {
                validator.validateWitness(BaseBlockWitness.fromBytes(candidate.witness), witnessBuilder)
                logger.info("Received snapshot info from peers, highest valid height was: $candidateHeaderHeight")

                if (candidateHeaderHeight <= params.snapshotSyncThreshold) {
                    snapshotSyncEvents.add(SnapshotSyncEvent.NO_SYNC_DUE_TO_BELOW_THRESHOLD)
                    logger.info("Snapshot height $candidateHeaderHeight is below threshold ${params.snapshotSyncThreshold}. Not syncing snapshot.")
                    return false
                }

                params.syncToExactHeight  = candidateHeaderHeight
                val rootHash = candidateHeader.getExtra()[SNAPSHOT_ROOT_EXTRA_HEADER]?.asByteArray() ?:
                    throw ProgrammerMistake("Snapshot root hash not found in block header")
                val blockHeaderContextData = getVerifiedContextData(candidate, rootHash)

                withWriteConnection(workerContext.engine.blockBuilderStorage, workerContext.blockchainConfiguration.chainID) { ctx ->
                    DatabaseAccess.of(ctx).apply {
                        pruneSnapshotSyncState(ctx)

                        syncState = SnapshotSyncState(candidateHeaderHeight, rootHash)
                        setSnapshotSyncState(ctx, syncState)

                        blockHeaderContextData.values.forEach {
                            val contextState = SnapshotSyncContextState(it.contextId, it.rootHash,
                                    0, it.datumIdMax ?: 0)
                            contextStates[it.contextId] = contextState
                            setSnapshotSyncContextState(ctx, contextState)
                        }
                    }
                    snapshotModuleByContextMap.values.forEach { it.initializeImport(ctx) }
                    true
                }

                setInitialNodeStates(candidate)

                snapshotSyncEvents.add(SnapshotSyncEvent.WILL_SYNC_FROM_NODES)
                logger.info("Snapshot sync starts from nodes: ${peerStatuses.getSyncablePeers(candidateHeaderHeight)}")
                return true
            } catch (e: Exception) {
                with("Received invalid snapshot header from peer: ${e.message}") {
                    logger.warn(this, e)
                    peerStatuses.maybeBlacklist(peerId, this)
                }
            }
        }

        return false
    }

    /**
     *  @param awaitsSnapshotHeights if true, expect snapshot height messages. If false, expect snapshot data messages.
     */
    private fun processMessages(awaitsSnapshotHeights: Boolean) {
        resetServedRequests()
        for ((peerId, _, message) in communicationManager.getPackets()) {
            if (peerStatuses.isBlacklisted(peerId)) {
                continue
            }
            if (message is GetBlockHeaderAndBlock || message is BlockHeader) {
                peerStatuses.confirmModern(peerId)
            }
            try {
                when (message) {
                    // We will answer any get call
                    is GetBlockAtHeight -> sendBlockAtHeight(peerId, message.height)
                    is GetBlockHeaderAndBlock -> sendBlockHeaderAndBlock(peerId, message.height, blockHeight.get())
                    is GetBlockRange -> sendBlockRangeFromHeight(peerId, message.startAtHeight, blockHeight.get())
                    is GetBlockSignature -> sendBlockSignature(peerId, message.blockRID)

                    is GetLatestSnapshotBlock -> sendLatestSnapshotHeight(peerId, workerContext.engine.blockBuilderStorage,
                            workerContext.blockchainConfiguration.chainID, workerContext.blockchainConfiguration.snapshot.levelsPerPage,
                            workerContext.appConfig.cryptoSystem)
                    is SnapshotBlockHeader -> {
                        if (awaitsSnapshotHeights) {
                            receivedLatestSnapshotHeight[peerId] = message
                            if (message.header.isNotEmpty()) {
                                logger.info("Got a snapshot header from $peerId. Adding it to snapshot header candidates.")
                            } else {
                                logger.info("Got no snapshot header from $peerId")
                            }
                        }
                    }
                    is SnapshotData -> {
                        if (!awaitsSnapshotHeights) {
                            verifyAndGetExpectedRequest(message, peerId)?.let { state ->
                                val preparedData = message.data.mapIndexed { index, datum ->
                                    FullSnapshotDatumData(
                                            message.datumIdFrom + index,
                                            datum.data,
                                            datum.data.merkleHash(workerContext.blockchainConfiguration.merkleHashCalculator),
                                            datum.isPermanent)
                                }

                                val (valid, end) = verifyDataProof(message.contextId, message.datumIdFrom, message.proof!!, preparedData)
                                if (valid) {
                                    if (!end) {
                                        requestNextSnapshotData(state, message)
                                    }

                                    val waitTime = lastStoredSnapshotDataTime[message.contextId]?.let {
                                        clock.millis() - it
                                    }

                                    val storeTime = measureTime {
                                        storeSnapshotData(message.contextId, preparedData, end)
                                    }
                                    logger.debug { "Stored ${preparedData.size} datums (${FileUtils.byteCountToDisplaySize(preparedData.sumOf { it.data.nrOfBytes() })}) from offset ${message.datumIdFrom} for context id ${message.contextId} in ${storeTime.toLong(DurationUnit.MILLISECONDS)} ms. End: $end. Wasted time from last store: $waitTime ms" }
                                    lastStoredSnapshotDataTime[message.contextId] = clock.millis()

                                    if (end) {
                                        logger.debug { "Snapshot end reached for context ${state.contextId}" }
                                        contextSyncRequests.remove(state.contextId)
                                    }
                                } else {
                                    with("Snapshot data received from $peerId is not valid for context ${state.contextId} and offset ${state.offset}") {
                                        logger.warn(this)
                                        peerStatuses.maybeBlacklist(peerId, this)
                                    }
                                    sendGetSnapshotData(state)
                                }
                            }
                        }
                    }

                    is EbftVersion -> logger.debug { "Received EbftVersion from peer $peerId" }
                }
            } catch (e: Exception) {
                logger.info("Couldn't handle message $message from peer $peerId. Ignoring and continuing", e)
            }
        }
    }

    private fun verifyDataProof(contextId: Long, datumIdFrom: Long, snapshotProof: SnapshotRangeProof, data: List<FullSnapshotDatumData>): Pair<Boolean, Boolean> {
        val leafs = TreeMap<Long, Hash>()
        data.forEach {
            leafs[it.datumId] = it.hash
        }
        val proof = RangeProof(snapshotProof.leftBoundaryHashes, snapshotProof.rightBoundaryHashes, snapshotProof.commonPath)
        return verifyRangeProof.verify(contextStates[contextId]!!.contextRootHash, proof, datumIdFrom, leafs.values.toList())
    }

    private fun syncSnapshotUntil() {

        snapshotSyncEvents.add(SnapshotSyncEvent.SYNCING)

        if (isProcessRunning()) {
            sendInitialSnapshotDataRequest()

            while (isProcessRunning() && contextSyncRequests.isNotEmpty()) {
                processMessages(false)
                processRequestTimeouts()
                sleep(params.loopInterval)
            }

            if (isProcessRunning() && contextSyncRequests.isEmpty()) {
                withWriteConnection(workerContext.engine.blockBuilderStorage, workerContext.blockchainConfiguration.chainID) { ctx ->
                    snapshotModuleByContextMap.values.forEach { it.finalizeImport(ctx) }
                    buildAndVerifySnapshot(ctx)
                    true
                }
            }
        }
    }

    private fun buildAndVerifySnapshot(ctx: EContext) {
        val localSnapshotRootHash = RootSnapshotBlockBuilder(ctx, syncState.height,
                workerContext.blockchainConfiguration.snapshot.levelsPerPage,
                workerContext.appConfig.cryptoSystem).build()

        if (syncState.rootHash.contentEquals(localSnapshotRootHash)) {
            DatabaseAccess.of(ctx).pruneSnapshotSyncState(ctx)
            snapshotSyncEvents.add(SnapshotSyncEvent.FINISHED_SUCCESSFULLY)
            logger.info("Finished snapshot syncing successfully")
        } else {
            logger.warn("Finished snapshot syncing, but root hashes do not match")
            throw ProgrammerMistake("Snapshot root hashes do not match")
        }
    }

    private fun storeSnapshotData(contextId: Long, data: List<FullSnapshotDatumData>, end: Boolean) {
        if (data.isNotEmpty()) {
            withWriteConnection(workerContext.engine.blockBuilderStorage, workerContext.blockchainConfiguration.chainID) { ctx ->
                DatabaseAccess.of(ctx).apply {
                    val module = snapshotModuleByContextMap[contextId] ?:
                        throw ProgrammerMistake("No module found for snapshot context id $contextId")
                    insertUpdatedDatum(ctx, contextId, data.map {
                        DatumInfo(it.datumId, it.hash, if (it.isPermanent) null else GtvEncoder.encodeGtv(it.data))
                    })
                    module.constructDatum(ctx, data.map {
                        SnapshotDatum(it.datumId, it.data, it.isPermanent)
                    })
                    if (end) {
                        removeSnapshotSyncContextState(ctx, contextId)
                    } else {
                        setSnapshotSyncContextStateOffset(ctx, contextId, data.last().datumId + 1)
                    }
                }

                true
            }
        }
    }

    private fun verifyAndGetExpectedRequest(message: SnapshotData, peerId: NodeRid): SnapshotContextState? {
        val contextState = contextSyncRequests[message.contextId]
        var error: String? = null
        if (contextState == null) {
            error = "Received snapshot data for unknown context ${message.contextId}"
        } else if (message.datumIdFrom != contextState.offset) {
            error = "Received incorrect offset in snapshot data for context ${message.contextId}, expected offset ${contextState.offset}, got ${message.datumIdFrom}"
        } else if (message.height != params.syncToExactHeight) {
            error = "Received snapshot data for wrong height, expected ${params.syncToExactHeight}, got ${message.height}"
        } else if (message.data.isEmpty()) {
            logger.debug { "Node $peerId does not have requested height ${message.height} in snapshot context ${message.contextId}. Node is removed from list and request is sent to another node." }
            peerStatuses.drained(peerId, -1)
            sendGetSnapshotData(contextState)
        } else if (message.proof == null) {
            error = "Node $peerId did not include snapshot proof for height ${message.height} in context ${message.contextId}."
        } else {
            return contextState
        }

        if (error != null) {
            logger.warn(error)
            peerStatuses.maybeBlacklist(peerId, error)
        }
        return null
    }

    private fun sendInitialSnapshotDataRequest() {
        contextStates.values.forEach {
            val state = SnapshotContextState(it.contextId, offset = it.datumIdOffset)
            contextSyncRequests[state.contextId] = state
            sendGetSnapshotData(state)
        }
    }

    private fun sendGetSnapshotData(contextState: SnapshotContextState) {
        val message = GetSnapshotData(params.syncToExactHeight, contextState.contextId, contextState.offset)
        val snapshotNodes = peerStatuses.getSyncablePeers(params.syncToExactHeight)
        val peer = communicationManager.sendToRandomPeer(message, snapshotNodes.minus(contextState.sentTo.toSet())).first ?: let {
            logger.info { "Snapshot data request has been sent to all available nodes without any response. Retrying with random nodes." }
            communicationManager.sendToRandomPeer(message, snapshotNodes).first
        }
        if (peer == null) {
            logger.info { "Couldn't find any node to send snapshot data request to for context ${contextState.contextId} and offset ${contextState.offset}." }
        } else {
            logger.debug { "Sent GetSnapshotData to peer $peer for context ${contextState.contextId}, offset ${contextState.offset}." +
                    (if (contextState.sentTo.isNotEmpty()) "Already sent to (${contextState.sentTo.size}): ${contextState.sentTo}" else "") }

            contextState.sentTo.add(peer)
        }
        contextState.timeSent = clock.millis()
    }

    private fun requestNextSnapshotData(state: SnapshotContextState, message: SnapshotData) {
        state.offset += message.data.size
        state.sentTo.clear()
        sendGetSnapshotData(state)
    }

    private fun processRequestTimeouts() {
        val now = clock.millis()
        contextSyncRequests
                .filterValues { it.timeSent + params.jobTimeout < now }
                .values
                .forEach {
                    logger.debug { "Snapshot request timed out for context ${it.contextId} and offset ${it.offset}, sending request to another node" }
                    it.sentTo.lastOrNull()?.let { peer ->
                        peerStatuses.unresponsive(peer, "Snapshot request timed out")
                    }
                    sendGetSnapshotData(it)
                }
    }

    private fun getVerifiedContextData(candidate: SnapshotBlockHeader, rootHash: ByteArray): Map<Long, SnapshotBlockHeaderContextData> {
        val rootHashOfContextHashes = verifyRangeProof.calculateMerkleRoot(
                candidate.contextData.map { it.rootHash },
                workerContext.blockchainConfiguration.snapshot.levelsPerPage)
        if (!rootHashOfContextHashes.contentEquals(rootHash)) {
            throw ProgrammerMistake("Merkle root of snapshot context data does not match")
        }
        return candidate.contextData.associateBy { it.contextId }
    }

    // Set nodes with candidate heigher syncable, the rest drained or unresponsive
    private fun setInitialNodeStates(candidate: SnapshotBlockHeader) {
        configuredPeers.minus(receivedLatestSnapshotHeight.keys).forEach {
            peerStatuses.unresponsive(it, "Never received LatestSnapshotBlock")
        }
        val syncableNodes = receivedLatestSnapshotHeight.filterValues { it.header.contentEquals(candidate.header) }
        peerStatuses.getAllPeers()
                .forEach {
                    if (syncableNodes.containsKey(it)) {
                        peerStatuses.markSyncable(it)
                    } else {
                        val height = receivedLatestSnapshotHeight[it]?.header?.let { bh ->
                            BlockHeaderData.fromBinary(bh).getHeight()
                        } ?: 0
                        peerStatuses.drained(it, height,
                                drainedTimeout = params.snapshotSyncPeerParameters.resurrectDrainedTime)
                    }
                }
    }

    private fun loadOngoingSyncState(): Boolean {
        return withReadConnection(workerContext.engine.blockBuilderStorage, blockchainConfiguration.chainID) { ctx ->
            val db = DatabaseAccess.of(ctx)
            syncState = db.getSnapshotSyncState(ctx) ?: return@withReadConnection false
            db.getAllSnapshotSyncContexts(ctx).forEach {
                contextStates[it.contextId] = it
            }
            params.syncToExactHeight = syncState.height
            configuredPeers.forEach { peerStatuses.addPeer(it) }
            true
        }
    }

    fun getSnapshotSyncEvents(): SnapshotSyncEvents {
        return snapshotSyncEvents
    }
}

/** Track active requests */
data class SnapshotContextState(
        val contextId: Long,
        var offset: Long = 0,
        var timeSent: Long = System.currentTimeMillis(),

        /** Nodes that have already been sent current request, in order */
        val sentTo: MutableList<NodeRid> = mutableListOf(),
)

data class FullSnapshotDatumData(val datumId: Long, val data: Gtv, val hash: Hash, val isPermanent: Boolean) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FullSnapshotDatumData

        if (datumId != other.datumId) return false
        if (isPermanent != other.isPermanent) return false
        if (data != other.data) return false
        if (!hash.contentEquals(other.hash)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = datumId.hashCode()
        result = 31 * result + isPermanent.hashCode()
        result = 31 * result + data.hashCode()
        result = 31 * result + hash.contentHashCode()
        return result
    }
}

class SnapshotSyncEvents(
    val events: MutableList<SnapshotSyncEvent> = mutableListOf()
) {
    fun add(event: SnapshotSyncEvent) {
        events.add(event)
    }
}

enum class SnapshotSyncEvent {
    WILL_CONTINUING_SYNC,
    NO_SYNC_DUE_TO_HEIGHT_NOT_0,
    NO_SYNC_DUE_TO_BELOW_THRESHOLD,
    WILL_SYNC_FROM_NODES,
    SYNCING,
    FINISHED_SUCCESSFULLY,
}

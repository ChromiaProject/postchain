// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.controller

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.gson.JsonArray
import io.micrometer.core.instrument.Metrics
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import mu.KLogging
import mu.withLoggingContext
import net.postchain.api.rest.BlockchainIidRef
import net.postchain.api.rest.BlockchainRef
import net.postchain.api.rest.BlockchainRidRef
import net.postchain.api.rest.Empty
import net.postchain.api.rest.ErrorBody
import net.postchain.api.rest.InfraVersion
import net.postchain.api.rest.Version
import net.postchain.api.rest.acceptQueryResponseSignatureHeader
import net.postchain.api.rest.afterHeightQuery
import net.postchain.api.rest.afterTimeQuery
import net.postchain.api.rest.beforeHeightQuery
import net.postchain.api.rest.beforeTimeQuery
import net.postchain.api.rest.binaryBody
import net.postchain.api.rest.blockBody
import net.postchain.api.rest.blockHeightBody
import net.postchain.api.rest.blockRidPath
import net.postchain.api.rest.blockchainNodeStateBody
import net.postchain.api.rest.blocksBody
import net.postchain.api.rest.configurationFeaturesOutBody
import net.postchain.api.rest.configurationInBody
import net.postchain.api.rest.configurationOutBody
import net.postchain.api.rest.containerQuery
import net.postchain.api.rest.controller.http4k.NettyWithCustomWorkerGroup
import net.postchain.api.rest.decodeTxQuery
import net.postchain.api.rest.decodedTxInfoExtBody
import net.postchain.api.rest.emptyBody
import net.postchain.api.rest.errorBody
import net.postchain.api.rest.excludeEmptyQuery
import net.postchain.api.rest.gtvJsonBody
import net.postchain.api.rest.heightPath
import net.postchain.api.rest.heightQuery
import net.postchain.api.rest.highestBlockHeightAnchoringCheckBody
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.api.rest.infraVersionBody
import net.postchain.api.rest.limitQuery
import net.postchain.api.rest.metadataBody
import net.postchain.api.rest.model.DecodedTransactionInfoExt
import net.postchain.api.rest.model.QueryResponseSignatureData
import net.postchain.api.rest.model.TxRid
import net.postchain.api.rest.nodeStatusBody
import net.postchain.api.rest.nodeStatusesBody
import net.postchain.api.rest.nullBody
import net.postchain.api.rest.nullJsonBody
import net.postchain.api.rest.pathPath
import net.postchain.api.rest.prettyGson
import net.postchain.api.rest.prettyJsonBody
import net.postchain.api.rest.proofBody
import net.postchain.api.rest.queryRidPath
import net.postchain.api.rest.rejectedTransactionsBody
import net.postchain.api.rest.signatureBody
import net.postchain.api.rest.signatureHeader
import net.postchain.api.rest.signerQuery
import net.postchain.api.rest.statusGtvBody
import net.postchain.api.rest.statusJsonBody
import net.postchain.api.rest.textBody
import net.postchain.api.rest.transactionsCountBody
import net.postchain.api.rest.txBody
import net.postchain.api.rest.txDataQuery
import net.postchain.api.rest.txInfoExtBody
import net.postchain.api.rest.txInfoExtsBody
import net.postchain.api.rest.txRidPath
import net.postchain.api.rest.txRidsBody
import net.postchain.api.rest.txsQuery
import net.postchain.api.rest.versionBody
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.configuration.KEY_FEATURES
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.rest.AnchoringChainCheck
import net.postchain.common.rest.HighestBlockHeightAnchoringCheck
import net.postchain.core.PmEngineIsAlreadyClosed
import net.postchain.core.block.BlockDetail
import net.postchain.core.block.BlockQueryHeightFilter
import net.postchain.core.block.BlockQueryTimeFilter
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.PubKey
import net.postchain.crypto.sha256Digest
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.ErrorValue
import net.postchain.debug.JsonNodeDiagnosticContext
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvException
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.GtvStream
import net.postchain.gtv.GtvString
import net.postchain.gtv.GtvType
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GtxQuery
import net.postchain.gtx.NON_STRICT_QUERY_ARGUMENT
import net.postchain.gtx.UnknownOperation
import net.postchain.gtx.UnknownQuery
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.managed.ManagedNodeDataSource
import net.postchain.managed.config.ManagedDataSourceAware
import org.greenbytes.http.sfv.ByteSequenceItem
import org.greenbytes.http.sfv.Dictionary
import org.greenbytes.http.sfv.StringItem
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Filter
import org.http4k.core.HttpTransaction
import org.http4k.core.Method.GET
import org.http4k.core.Method.OPTIONS
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.ACCEPTED
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SERVICE_UNAVAILABLE
import org.http4k.core.Status.Companion.TEMPORARY_REDIRECT
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.http4k.core.maxAge
import org.http4k.core.public
import org.http4k.core.queries
import org.http4k.core.then
import org.http4k.core.toParametersMap
import org.http4k.core.with
import org.http4k.filter.AllowAll
import org.http4k.filter.CachingFilters.CacheResponse
import org.http4k.filter.CorsPolicy
import org.http4k.filter.MicrometerMetrics
import org.http4k.filter.OriginPolicy
import org.http4k.filter.ServerFilters
import org.http4k.format.auto
import org.http4k.lens.ContentNegotiation
import org.http4k.lens.Header
import org.http4k.lens.Invalid
import org.http4k.lens.LensFailure
import org.http4k.lens.Meta
import org.http4k.lens.ParamMeta
import org.http4k.lens.RequestKey
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.server.ServerConfig
import org.http4k.server.asServer
import java.io.Closeable
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeoutException

const val BLOCKCHAIN_RID = "blockchainRid"

const val UNAUTHORIZED_INVALID_SIGNATURE = "Invalid signature"
const val UNAUTHORIZED_REQUIRE_SIGNATURE_IN_MANAGED_MODE = "Configuration must be signed"
const val FORBIDDEN_CONFIG_NOT_SIGNED_BY_PROVIDER = "Configuration must be signed by blockchain provider"

const val DATA_TRUNCATED_HEADER = "X-Data-Truncated"
const val TRANSACTION_TIMESTAMP = "X-Transaction-Timestamp"
const val BLOCK_HEIGHT_HEADER = "X-Block-Height"
const val QUERY_RESPONSE_SIGNATURE = "X-Query-Response-Signature"

const val QUERY_TYPE = "type"
const val QUERY_ARGS = "~args"

/**
 * Implements the REST API.
 *
 * @param requestConcurrency  number of incoming HTTP requests to handle concurrently,
 *                            specify 0 for default value based on number of available processor cores
 * @param requestConcurrencyLocal Maximum number of threads in `chainRequestConcurrency` to be used for local model requests.
 * @param requestConcurrencyExternal Maximum number of threads in `chainRequestConcurrency` to be used for external model requests.
 * @param chainRequestConcurrency  number of incoming HTTP requests to handle concurrently per blockchain,
 *                                 specify `-1` for no extra limit per chain.
 * @param containerRequestConcurrency  number of incoming HTTP requests to handle concurrently per container,
 *                                 specify `-1` for no extra limit per container.
 */
class RestApi(
        listenPort: Int,
        val basePath: String,
        private val nodeDiagnosticContext: NodeDiagnosticContext = JsonNodeDiagnosticContext(),
        gracefulShutdown: Boolean = true,
        requestConcurrency: Int = 0,
        requestConcurrencyLocal: Int = -1,
        requestConcurrencyExternal: Int = -1,
        private val chainRequestConcurrency: Int = -1,
        private val containerRequestConcurrency: Int = -1,
        private val subnodeHttpRedirect: Boolean = false,
        val maxRequestBodySize: Int = RestApiConfig.DEFAULT_MAX_REQUEST_BODY_SIZE,
        val maxDataSize: Int = RestApiConfig.DEFAULT_MAX_DATA_SIZE,
) : Modellable, Closeable {

    companion object : KLogging() {
        const val REST_API_VERSION = 22

        private const val MAX_NUMBER_OF_BLOCKS_PER_REQUEST = 100
        private const val DEFAULT_ENTRY_RESULTS_REQUEST = 25
        private const val MAX_NUMBER_OF_TXS_PER_REQUEST = 600

        private val blockchainRidPattern = Regex("([0-9a-fA-F]+)")
        private val chainIidPattern = Regex("iid_([0-9]+)")
    }

    private val internalModelRequestSemaphores: Semaphore? = if (requestConcurrencyLocal > 0)
        Semaphore(requestConcurrencyLocal) else null
    private val externalModelRequestSemaphores: Semaphore? = if (requestConcurrencyExternal > 0)
        Semaphore(requestConcurrencyExternal) else null
    private val containerRequestSemaphores = mutableMapOf<String, Semaphore>()
    private val models = mutableMapOf<Pair<BlockchainRid, String>, Pair<ChainModel, Semaphore?>>() // (blockchainRid, container) -> (chainModel, semaphore)
    private val bridByIID = mutableMapOf<Long, BlockchainRid>()

    override fun attachModel(blockchainRid: BlockchainRid, chainModel: ChainModel, container: String) {
        models[blockchainRid to container] = chainModel to if (chainRequestConcurrency > 0)
            Semaphore(chainRequestConcurrency) else null
        bridByIID[chainModel.chainIID] = blockchainRid
        if (containerRequestConcurrency > 0) {
            if (chainModel is ExternalModel) {
                containerRequestSemaphores.computeIfAbsent(chainModel.directoryContainer) { Semaphore(containerRequestConcurrency) }
            }
        }
    }

    override fun detachModel(blockchainRid: BlockchainRid, container: String) {
        val model = models.remove(blockchainRid to container)
        if (model != null) {
            bridByIID.remove(model.first.chainIID)
        } else throw ProgrammerMistake("Blockchain $blockchainRid not attached")
    }

    override fun retrieveModels(blockchainRid: BlockchainRid): List<ChainModel> =
            models.filterKeys { it.first == blockchainRid }.map { it.value.first }

    fun actualPort(): Int = server.port()

    private fun blockchainRefFilter(failOnNonLive: Boolean) = Filter { next ->
        { request ->
            val ref = request.path(BLOCKCHAIN_RID)?.let { parseBlockchainRid(it) }
            if (ref != null) {
                val blockchainRid = resolveBlockchain(ref)
                val container = containerQuery(request)
                val (chainModel, chainSemaphore) = chainModel(blockchainRid, container)
                if (failOnNonLive && !chainModel.live) throw UnavailableException("Blockchain is unavailable")
                withLoggingContext(
                        BLOCKCHAIN_RID_TAG to blockchainRid.toHex(),
                        CHAIN_IID_TAG to chainModel.chainIID.toString()) {

                    if (chainModel is ExternalModel && subnodeHttpRedirect) {
                        val request0 = request.removeQuery("container")
                        Response(TEMPORARY_REDIRECT).header("Location", chainModel.path + request0.uri.toString().substring(basePath.length))
                    } else {
                        val acquireSemaphores = mutableListOf(chainSemaphore to {
                            "Too many concurrent requests for blockchain $blockchainRid"
                        })

                        if (chainModel is ExternalModel) {
                            acquireSemaphores.add(externalModelRequestSemaphores to {
                                "Too many concurrent requests for subnode containers"
                            })
                            acquireSemaphores.add(containerRequestSemaphores[chainModel.directoryContainer] to {
                                "Too many concurrent requests for container ${chainModel.directoryContainer}"
                            })
                        } else {
                            acquireSemaphores.add(internalModelRequestSemaphores to {
                                "Too many concurrent requests for internal models"
                            })
                        }

                        maybeTryAcquireSemaphore(acquireSemaphores, request) {
                            next(request.with(chainModelKey of chainModel, blockchainRidKey of blockchainRid))
                        }
                    }
                }
            } else {
                throw invalidBlockchainRid()
            }
        }
    }

    private val blockchainMetricsFilter = ServerFilters.MicrometerMetrics.RequestTimer(Metrics.globalRegistry, labeler = ::blockchainLabeler)

    private fun blockchainLabeler(httpTransaction: HttpTransaction): HttpTransaction {
        val model = chainModelKey(httpTransaction.request)
        val blockchainRid = blockchainRidKey(httpTransaction.request)
        return httpTransaction
                .label(CHAIN_IID_TAG, model.chainIID.toString())
                .label(BLOCKCHAIN_RID_TAG, blockchainRid.toHex())
    }

    private val externalRoutingFilter = Filter { next ->
        { request ->
            if (logger.isDebugEnabled) {
                logger.debug("""[${request.source?.address ?: "(unknown)"}] ${request.method} ${request.uri.path}${if (request.uri.query.isBlank()) "" else "?${request.uri.query}"}
                    |${request.headers.joinToString("\n") { "${it.first}: ${it.second}" }}""".trimMargin())
                // Assuming the content-type is correctly set, we will avoid logging binary request bodies
                if (Header.CONTENT_TYPE(request)?.equalsIgnoringDirectives(ContentType.OCTET_STREAM) != true
                        && (request.body.length ?: 0) > 0
                        && request.header("content-encoding") != "gzip") {
                    logger.debug { "Request body: ${String(request.body.payload.array())}" }
                }
            }
            val response = when (val chainModel = chainModelKey(request)) {
                is Model -> {
                    logger.trace { "Local REST API model found: $chainModel" }
                    next(request.with(modelKey of chainModel))
                }

                is ExternalModel -> {
                    logger.trace { "External REST API model found: $chainModel" }
                    chainModel(request.removeQuery("container"))
                }
            }
            if (logger.isDebugEnabled) {
                logger.debug("${response.status}\n${response.headers.joinToString("\n") { "${it.first}: ${it.second}" }}")
                // Assuming the content-type is correctly set, we will avoid logging binary response bodies
                if (Header.CONTENT_TYPE(response)?.equalsIgnoringDirectives(ContentType.OCTET_STREAM) != true
                        && (response.body.length ?: 0) > 0
                        && response.header("content-encoding") != "gzip") {
                    logger.debug("Response body: ${String(response.body.payload.array())}")
                }
            }
            response
        }
    }

    private val liveBlockchain = blockchainRefFilter(true).then(blockchainMetricsFilter).then(externalRoutingFilter).then(ServerFilters.GZip())
    private val blockchain = blockchainRefFilter(false).then(blockchainMetricsFilter).then(externalRoutingFilter).then(ServerFilters.GZip())

    private val immutableResponse = CacheResponse.MaxAge(Duration.ofDays(365))
    private val volatileResponse = CacheResponse.NoCache()

    private val app = routes(
            // Static content
            "/" bind GET to { serveStaticFile("/restapi-root/index.html") },
            "/_debug" bind GET to { serveStaticFile("/restapi-root/_debug/index.html") },
            "/apidocs" bind GET to { serveStaticFile("/restapi-docs/index.html") },
            "/apidocs/{file}" bind GET to { request: Request ->
                val file = request.path("file") ?: ""
                when (file) {
                    "index.html", "pdf.html" -> {
                        serveStaticFile("/restapi-docs/$file")
                    }
                    "postchain-restapi.yaml" -> {
                        serveStaticFile("/restapi-docs/postchain-restapi.yaml", "text/yaml")
                    }
                    else -> Response(NOT_FOUND)
                }
            },

            "/version" bind GET to ::getVersion,
            "/version/{blockchainRid}" bind GET to liveBlockchain.then(::getBlockchainVersion),
            "/infrastructure_version" bind GET to ::getInfraVersion,
            "/infrastructure_version/{blockchainRid}" bind GET to liveBlockchain.then(::getBlockchainInfraVersion),

            "/tx/{blockchainRid}" bind POST to liveBlockchain.then(::postTransaction),
            "/tx/{blockchainRid}/waiting" bind GET to blockchain.then(volatileResponse).then(::getWaitingTransactions),
            "/tx/{blockchainRid}/waiting/{txRid}" bind GET to blockchain.then(volatileResponse).then(::getWaitingTransaction),
            "/tx/{blockchainRid}/rejected" bind GET to blockchain.then(volatileResponse).then(::getRejectedTransactions),
            "/tx/{blockchainRid}/{txRid}" bind GET to blockchain.then(immutableResponse).then(::getTransaction),
            "/tx/{blockchainRid}/{txRid}/confirmationProof" bind GET to blockchain.then(immutableResponse).then(::getConfirmationProof),
            "/tx/{blockchainRid}/{txRid}/status" bind GET to liveBlockchain.then(volatileResponse).then(::getTransactionStatus),
            "/transactions/{blockchainRid}/count" bind GET to blockchain.then(volatileResponse).then(::getTransactionsCount),
            "/transactions/{blockchainRid}/{txRid}" bind GET to blockchain.then(immutableResponse).then(::getTransactionInfo),
            "/transactions/{blockchainRid}" bind GET to blockchain.then(volatileResponse).then(::getTransactionsInfo),

            "/blocks/{blockchainRid}" bind GET to blockchain.then(volatileResponse).then(::getBlocks),
            "/blocks/{blockchainRid}/{blockRid}" bind GET to blockchain.then(immutableResponse).then(::getBlock),
            "/blocks/{blockchainRid}/height/{height}" bind GET to blockchain.then(immutableResponse).then(::getBlockByHeight),
            "/blocks/{blockchainRid}/confirm/{blockRid}" bind GET to liveBlockchain.then(::confirmBlock),

            "/query/{blockchainRid}" bind GET to liveBlockchain.then(::getQuery),
            "/query/{blockchainRid}" bind POST to liveBlockchain.then(::postQuery),
            // Direct query. That should be used as an example: <img src="http://node/dquery/brid?type=get_picture&id=4555" />
            "/dquery/{blockchainRid}" bind GET to liveBlockchain.then(::directQuery),
            // Web query. That should be used as an example: <img src="http://node/web_query/brid/get_picture?id=4555" />
            "/web_query/{blockchainRid}/{path:.*}" bind GET to liveBlockchain.then(::webQuery),
            "/query_gtv/{blockchainRid}" bind GET to liveBlockchain.then(::getQueryGtv),
            "/query_gtv/{blockchainRid}" bind POST to liveBlockchain.then(::postQueryGtv),
            "/query_async/{blockchainRid}" bind POST to liveBlockchain.then(::postQueryAsync),
            "/query_async/{blockchainRid}/{queryRid}" bind GET to liveBlockchain.then(::getQueryAsync),

            "/node/{blockchainRid}/my_status" bind GET to liveBlockchain.then(volatileResponse).then(::getNodeStatus),
            "/node/{blockchainRid}/statuses" bind GET to liveBlockchain.then(volatileResponse).then(::getNodeStatuses),
            "/brid/{blockchainRid}" bind GET to liveBlockchain.then(::getBlockchainRid),

            "/blockchain/{blockchainRid}/height" bind GET to blockchain.then(volatileResponse).then(::getCurrentHeight),
            "/blockchain/{blockchainRid}/nodestate" bind GET to blockchain.then(::getBlockchainNodeState),

            "/config/{blockchainRid}" bind GET to liveBlockchain.then(::getBlockchainConfiguration),
            "/config/{blockchainRid}" bind POST to liveBlockchain.then(::validateBlockchainConfiguration),
            "/config/{blockchainRid}/next_height" bind GET to liveBlockchain.then(::getNextBlockchainConfigurationHeight),
            "/config/{blockchainRid}/features" bind GET to liveBlockchain.then(::getBlockchainConfigurationFeatures),

            "/errors/{blockchainRid}" bind GET to blockchain.then(volatileResponse).then(::getErrors),

            "/metadata/{blockchainRid}" bind GET to liveBlockchain.then(::getMetadata),

            "/highest_block_height_anchoring_check/{blockchainRid}" bind GET to ::getHighestBlockHeightAnchoringCheck,
    )

    private fun serveStaticFile(resourcePath: String, contentType: String = "text/html"): Response {
        val content = this::class.java.getResourceAsStream(resourcePath)
                ?.use { it.readBytes() }
                ?: throw NotFoundError("${resourcePath.substringAfterLast('/')} not found")

        return Response(OK)
                .with(Header.CONTENT_TYPE.of(ContentType(contentType)))
                .body(String(content, Charsets.UTF_8))
    }

    @Suppress("UNUSED_PARAMETER")
    private fun getVersion(request: Request): Response = Response(OK).with(
            versionBody of Version(REST_API_VERSION)
    )

    private fun getBlockchainVersion(request: Request): Response {
        val model = model(request)
        val version = model.getVersion()
        return Response(OK).with(versionBody of version)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun getInfraVersion(request: Request): Response = Response(OK).with(
            infraVersionBody of InfraVersion(
                    postchain = nodeDiagnosticContext[DiagnosticProperty.VERSION]?.value?.toString().orEmpty(),
                    infrastructure = nodeDiagnosticContext[DiagnosticProperty.INFRASTRUCTURE_NAME]?.value?.toString().orEmpty(),
                    infrastructureVersion = nodeDiagnosticContext[DiagnosticProperty.INFRASTRUCTURE_VERSION]?.value?.toString().orEmpty(),
                    restApi = REST_API_VERSION.toString(),
                    databaseServerVersion = nodeDiagnosticContext[DiagnosticProperty.DATABASE_SERVER_VERSION]?.value?.toString().orEmpty()
            )
    )

    private fun getBlockchainInfraVersion(request: Request): Response {
        val model = model(request)
        val infraVersion = model.getInfrastructureVersion()
        return Response(OK).with(infraVersionBody of infraVersion)
    }

    private fun postTransaction(request: Request): Response {
        val model = model(request)
        val tx = ContentNegotiation.auto(txBody, binaryBody)(request)
        model.postTransaction(tx)
        return Response(OK).with(emptyBody.outbound(request) of Empty)
    }

    private fun getWaitingTransactions(request: Request): Response {
        val model = model(request)
        val waitingTransactions = model.getWaitingTransactions()
        return Response(OK).with(txRidsBody of waitingTransactions)
    }

    private fun getWaitingTransaction(request: Request): Response {
        val model = model(request)
        val txRid = txRidPath(request)
        val (txData, txTimestamp) = model.getWaitingTransaction(txRid)
                ?: throw NotFoundError("Can't find waiting transaction with RID: $txRid")
        return Response(OK).header(TRANSACTION_TIMESTAMP, txTimestamp.toEpochMilli().toString()).with(binaryBody of txData)
    }

    private fun getRejectedTransactions(request: Request): Response {
        val model = model(request)
        val rejectedTransactions = model.getRejectedTransactions()
        return Response(OK).with(rejectedTransactionsBody of rejectedTransactions)
    }

    private fun getTransaction(request: Request): Response {
        val tx = runTxActionOnModel(model(request), txRidPath(request)) { model, txRID ->
            model.getTransaction(txRID)
        }
        return Response(OK).with(ContentNegotiation.auto(txBody, binaryBody).outbound(request) of tx)
    }

    private fun getTransactionInfo(request: Request): Response {
        val txData = txDataQuery(request) != false
        val decodeTx = decodeTxQuery(request) == true
        if (decodeTx && !txData) {
            throw IllegalArgumentException("Cannot decode transaction without transaction data")
        }
        val txInfo = runTxActionOnModel(model(request), txRidPath(request)) { model, txRID ->
            model.getTransactionInfo(txRID, includeTxData = txData)
        }
        return if (decodeTx) {
            Response(OK).with(decodedTxInfoExtBody of DecodedTransactionInfoExt.build(txInfo))
        } else {
            Response(OK).with(txInfoExtBody of txInfo)
        }
    }

    private fun getTransactionsInfo(request: Request): Response {
        val model = model(request)
        val limit = limitQuery(request)?.coerceIn(0, MAX_NUMBER_OF_TXS_PER_REQUEST)
                ?: DEFAULT_ENTRY_RESULTS_REQUEST
        val timeFilter = BlockQueryTimeFilter(
                beforeTimeQuery(request) ?: Long.MAX_VALUE,
                afterTimeQuery(request) ?: -1
        )
        val signer = signerQuery(request)
        val (transactionInfoExts, truncated) = if (signer != null) {
            model.getTransactionsInfoBySigner(timeFilter, limit, PubKey(signer), maxDataSize)
        } else {
            model.getTransactionsInfo(timeFilter, limit, maxDataSize)
        }
        return Response(OK).with(txInfoExtsBody of transactionInfoExts)
                .header(DATA_TRUNCATED_HEADER, truncated.toString())
    }

    private fun getTransactionsCount(request: Request): Response {
        val model = model(request)
        val lastTransactionNumber = model.getLastTransactionNumber()
        return Response(OK).with(transactionsCountBody of lastTransactionNumber)
    }

    private fun getConfirmationProof(request: Request): Response {
        val proof = runTxActionOnModel(model(request), txRidPath(request)) { model, txRID ->
            model.getConfirmationProof(txRID)
        }
        return Response(OK).with(proofBody.outbound(request) of proof)
    }

    private fun getTransactionStatus(request: Request): Response {
        val status = runTxActionOnModel(model(request), txRidPath(request)) { model, txRID ->
            model.getStatus(txRID)
        }
        return Response(OK).with(ContentNegotiation.auto(statusJsonBody, statusGtvBody)
                .outbound(request) of status)
    }

    private fun getBlocks(request: Request): Response {
        val model = model(request)
        val beforeTime = beforeTimeQuery(request)
        val afterTime = afterTimeQuery(request)
        val beforeHeight = beforeHeightQuery(request)
        val afterHeight = afterHeightQuery(request)
        if ((afterTime != null || beforeTime != null) && (afterHeight != null || beforeHeight != null)) {
            throw UserMistake("Cannot filter on both time and height at the same time")
        }
        val excludeEmpty = excludeEmptyQuery(request) == true
        if (excludeEmpty && (beforeHeight == null || afterHeight == null) && (beforeTime == null || afterTime == null)) {
            throw UserMistake("exclude-empty can only be used with height or time filter")
        }
        val limit = limitQuery(request)?.coerceIn(0, MAX_NUMBER_OF_BLOCKS_PER_REQUEST)
                ?: DEFAULT_ENTRY_RESULTS_REQUEST
        val txHashesOnly = txsQuery(request) != true

        val (blockDetails, truncated) = if (beforeTime != null || afterTime != null) {
            val timeFilter = BlockQueryTimeFilter(
                    beforeTime ?: Long.MAX_VALUE,
                    afterTime ?: -1,
            )
            model.getBlocksBetweenTimes(timeFilter, limit, txHashesOnly, maxDataSize, excludeEmpty)
        } else {
            val heightFilter = BlockQueryHeightFilter(
                    beforeHeight ?: Long.MAX_VALUE,
                    afterHeight ?: -1,
            )
            model.getBlocksBetweenHeights(heightFilter, limit, txHashesOnly, maxDataSize, excludeEmpty)
        }
        return Response(OK).with(blocksBody of blockDetails)
                .header(DATA_TRUNCATED_HEADER, truncated.toString())
    }

    private fun getBlock(request: Request): Response {
        val model = model(request)
        val blockRID = blockRidPath(request)
        val txHashesOnly = txsQuery(request) != true
        val block = model.getBlock(blockRID, txHashesOnly)
        return blockResponse(block, request)
    }

    private fun getBlockByHeight(request: Request): Response {
        val model = model(request)
        val height = heightPath(request)
        val txHashesOnly = txsQuery(request) != true
        val block = model.getBlock(height, txHashesOnly)
        return blockResponse(block, request)
    }

    private fun confirmBlock(request: Request): Response {
        val model = model(request)
        val blockRID = blockRidPath(request)
        val signature = model.confirmBlock(blockRID)
        return if (signature != null) {
            Response(OK).with(signatureBody.outbound(request) of signature)
        } else {
            Response(OK).with(nullBody.outbound(request) of Unit)
        }
    }

    private fun blockResponse(block: BlockDetail?, request: Request): Response = if (block != null) {
        Response(OK).with(blockBody.outbound(request) of block)
    } else {
        Response(OK).with(nullBody.outbound(request) of Unit)
    }

    private fun getQuery(request: Request): Response {
        val model = model(request)
        val query = extractGetQuery(request.uri.queries().toParametersMap())
        val queryResult = model.query(query)
        val response = Response(OK).with(gtvJsonBody of queryResult)
        return getQueryResponse(model, response)
    }

    private fun postQuery(request: Request): Response {
        val model = model(request)
        val gtxQuery = gtvJsonBody(request)
        val query = parseQuery(gtxQuery)
        val queryResult = model.query(query)
        return Response(OK).with(gtvJsonBody of queryResult)
    }

    private fun directQuery(request: Request): Response {
        val model = model(request)
        val query = extractGetQuery(request.uri.queries().toParametersMap())
        val array = model.query(query).asArray()
        if (array.size < 2) {
            throw UserMistake("Response should have at least two parts: content-type and content (and optionally cache TTL in seconds)")
        }
        val contentType = array[0].asString()
        val content = array[1]
        val cacheTtlSeconds = if (array.size > 2) array[2].asInteger() else -1
        return webQueryResponse(model, content, contentType, cacheTtlSeconds)
    }

    private fun webQuery(request: Request): Response {
        val model = model(request)
        val path = pathPath(request).split('/')
        val queryName = path.firstOrNull() ?: throw UserMistake("Missing query type")
        val queryParams = gtv(request.uri.queries().toParametersMap().mapValues {
            gtv(it.value.map { v -> if (v == null) GtvNull else gtv(v) })
        })
        val query = GtxQuery(queryName, gtv(mapOf(
                "path" to gtv(path.drop(1).map { gtv(it) }),
                "query_params" to queryParams
        )))
        val res = model.query(query)
        if (res.type != GtvType.DICT)
            throw UserMistake("web_query response must be a dict with at least 'content_type' and 'content' (and optionally 'cache_ttl_seconds')")
        val dict = res.asDict()
        val contentType = dict["content_type"]?.asString()
                ?: throw UserMistake("web_query response must have content_type")
        val content = dict["content"] ?: throw UserMistake("web_query response must have content")
        val cacheTtlSeconds = dict["cache_ttl_seconds"]?.asInteger() ?: -1
        return webQueryResponse(model, content, contentType, cacheTtlSeconds)
    }

    private fun webQueryResponse(model: Model, content: Gtv, contentType: String, cacheTtlSeconds: Long): Response {
        val response = when (content) {
            is GtvString -> Response(OK)
                    .with(Header.CONTENT_TYPE.of(ContentType(contentType)))
                    .body(content.asString())

            is GtvByteArray -> Response(OK)
                    .with(Header.CONTENT_TYPE.of(ContentType(contentType)))
                    .body(Body(ByteBuffer.wrap(content.asByteArray())))

            is GtvStream -> Response(OK)
                    .with(Header.CONTENT_TYPE.of(ContentType(contentType)))
                    .body(Body(content.stream, content.length))

            else -> throw UserMistake("Unexpected content: ${content.javaClass.name}")
        }
        return getQueryResponse(model, response, cacheTtlSeconds)
    }

    private fun getQueryGtv(request: Request): Response {
        val model = model(request)
        val query = extractGetQuery(request.uri.queries().toParametersMap())
        val (queryResult, blockHeight) = model.queryWithHeight(query)
        return getQueryResponse(model, Response(OK)
                .with(binaryBody of GtvEncoder.encodeGtv(queryResult)))
                .header(BLOCK_HEIGHT_HEADER, blockHeight.toString())
    }

    private fun postQueryGtv(request: Request): Response {
        val model = model(request)
        val query = binaryBody(request)
        val gtvQuery = try {
            GtxQuery.decode(query)
        } catch (e: GtvException) {
            logger.debug { "Invalid GTV data in POST /query_gtv: $e" }
            throw IllegalArgumentException("Invalid GTV data")
        }
        val (queryResult, blockHeight) = model.queryWithHeight(gtvQuery)
        return Response(OK)
                .with(binaryBody of GtvEncoder.encodeGtv(queryResult))
                .header(BLOCK_HEIGHT_HEADER, blockHeight.toString())
                .let {
                    if (acceptQueryResponseSignatureHeader(request))
                        it.header(QUERY_RESPONSE_SIGNATURE, makeQueryResponseSignature(model, QueryResponseSignatureData(
                                name = gtvQuery.name,
                                args = gtvQuery.args,
                                height = blockHeight,
                                response = queryResult)))
                    else it
                }
    }

    private fun makeQueryResponseSignature(model: Model, queryResponseSignatureData: QueryResponseSignatureData): String {
        val hash = GtvObjectMapper.toGtvDictionary(queryResponseSignatureData).merkleHash(GtvMerkleHashCalculatorV2(::sha256Digest))
        val sigMaker = model.getBlockSigMaker()
        val signature = sigMaker.signDigest(hash)
        return Dictionary.valueOf(mapOf(
                "alg" to StringItem.valueOf(sigMaker.id),
                "subject" to ByteSequenceItem.valueOf(signature.subjectID),
                "sig" to ByteSequenceItem.valueOf(signature.data),
        )).serialize()
    }

    private fun postQueryAsync(request: Request): Response {
        val model = model(request)
        val query = binaryBody(request)
        val gtvQuery = try {
            GtxQuery.decode(query)
        } catch (e: GtvException) {
            logger.debug { "Invalid GTV data in POST /query_async: $e" }
            throw IllegalArgumentException("Invalid GTV data")
        }
        model.enqueueQuery(query = gtvQuery)
        return Response(ACCEPTED).with(emptyBody.outbound(request) of Empty)
    }

    private fun getQueryAsync(request: Request): Response {
        val model = model(request)
        val queryRid = queryRidPath(request)
        val response = model.fetchQueryResponse(queryRid)
        return Response(OK)
                .with(binaryBody of GtvEncoder.encodeGtv(GtvObjectMapper.toGtvDictionary(response)))
                .let { if (response.blockHeight != null) it.header(BLOCK_HEIGHT_HEADER, response.blockHeight.toString()) else it }
    }

    private fun getQueryResponse(model: Model, response: Response, cacheTtlSeconds: Long = -1): Response =
            getQueryResponse(response, if (cacheTtlSeconds > 0) cacheTtlSeconds else model.queryCacheTtlSeconds)

    private fun getQueryResponse(response: Response, cacheTtlSeconds: Long): Response = if (cacheTtlSeconds > 0) {
        val ttl = Duration.ofSeconds(cacheTtlSeconds)
        response.public().maxAge(ttl)
    } else {
        response
    }

    private fun getNodeStatus(request: Request): Response {
        val model = model(request)
        return Response(OK).with(nodeStatusBody of model.nodeStatusQuery())
    }

    private fun getNodeStatuses(request: Request): Response {
        val model = model(request)
        return Response(OK).with(nodeStatusesBody of model.nodePeersStatusQuery())
    }

    @Suppress("UNCHECKED_CAST")
    private fun getHighestBlockHeightAnchoringCheck(request: Request): Response {
        val blockchainRid = request.path(BLOCKCHAIN_RID)?.let { BlockchainRid(it.hexStringToByteArray()) }
                ?: throw ProgrammerMistake("No blockchain RID in route path")
        val cacCheck = nodeDiagnosticContext[DiagnosticProperty.BLOCKCHAIN_HIGHEST_BLOCK_HEIGHT_CLUSTER_ANCHORING_CHECK]?.value as? MutableMap<BlockchainRid, AnchoringChainCheck>
        val sacCheck = nodeDiagnosticContext[DiagnosticProperty.BLOCKCHAIN_HIGHEST_BLOCK_HEIGHT_SYSTEM_ANCHORING_CHECK]?.value as? MutableMap<BlockchainRid, AnchoringChainCheck>
        val evmCheck = nodeDiagnosticContext[DiagnosticProperty.BLOCKCHAIN_HIGHEST_BLOCK_HEIGHT_EVM_ANCHORING_CHECK]?.value as? MutableMap<BlockchainRid, AnchoringChainCheck>
        return Response(OK).with(highestBlockHeightAnchoringCheckBody of HighestBlockHeightAnchoringCheck(cacCheck?.get(blockchainRid), sacCheck?.get(blockchainRid), evmCheck?.get(blockchainRid)))
    }

    private fun getBlockchainRid(request: Request): Response {
        val model = model(request)
        return Response(OK).with(textBody of model.blockchainRid.toHex())
    }

    private fun getCurrentHeight(request: Request): Response {
        val model = model(request)
        val blockHeight = model.getCurrentBlockHeight()
        return Response(OK).with(blockHeightBody of blockHeight)
    }

    private fun getBlockchainNodeState(request: Request): Response {
        val model = model(request)
        val blockchainNodeState = model.getBlockchainNodeState()
        return Response(OK).with(blockchainNodeStateBody of blockchainNodeState)
    }

    private fun getBlockchainConfiguration(request: Request): Response {
        val model = model(request)
        val height = heightQuery(request)
        val configuration = model.getBlockchainConfiguration(height)
                ?: throw UserMistake("Failed to find configuration")
        val response = Response(OK).with(configurationOutBody.outbound(request) of configuration)
        return if (height == -1L) {
            volatileResponse.then { response }(request)
        } else {
            response
        }
    }

    private fun getBlockchainConfigurationFeatures(request: Request): Response {
        val model = model(request)
        val height = heightQuery(request)
        val configuration = model.getBlockchainConfiguration(height)
                ?: throw UserMistake("Failed to find configuration")
        val configGtv = GtvDecoder.decodeGtv(configuration)
        val features = configGtv.asDict()[KEY_FEATURES] ?: gtv(emptyMap())
        val response = Response(OK).with(configurationFeaturesOutBody.outbound(request) of features)
        return if (height == -1L) {
            volatileResponse.then { response }(request)
        } else {
            response
        }
    }

    private fun getNextBlockchainConfigurationHeight(request: Request): Response {
        val model = model(request)
        val height = heightQuery(request)
        val nextHeight = model.getNextBlockchainConfigurationHeight(height)
        return if (nextHeight != null) {
            Response(OK).with(blockHeightBody of nextHeight)
        } else {
            Response(OK).with(nullJsonBody of Unit)
        }
    }

    private fun validateBlockchainConfiguration(request: Request): Response {
        val model = model(request)
        val configuration = configurationInBody(request)
        val signatures = signatureHeader(request)

        // Require signed configuration if in managed mode
        withManagedDataSource(model) { managedDataSource, cryptoSystem ->

            if (signatures.isNullOrEmpty()) {
                throw UnauthorizedException(UNAUTHORIZED_REQUIRE_SIGNATURE_IN_MANAGED_MODE)
            }

            val configHash = BlockchainConfigurationData.merkleHash(configuration)
            if (!signatures.all { cryptoSystem.verifyDigest(configHash, it) }) {
                throw UnauthorizedException(UNAUTHORIZED_INVALID_SIGNATURE)
            }

            if (!signatures.any {
                        var isProvider = false
                        try {
                            isProvider = managedDataSource.isBlockchainProvider(PubKey(it.subjectID), model.blockchainRid)
                        } catch (e: Exception) {
                            logger.debug { "Is blockchain provider query failed: ${e.message}" }
                        }
                        isProvider
                    }) {
                throw ForbiddenException(FORBIDDEN_CONFIG_NOT_SIGNED_BY_PROVIDER)
            }
        }

        try {
            model.validateBlockchainConfiguration(configuration)
        } catch (e: UserMistake) {
            throw e
        } catch (e: Exception) {
            throw UserMistake("Invalid configuration: ${e.message}", e)
        }
        return Response(OK).with(emptyBody.outbound(request) of Empty)
    }

    private fun withManagedDataSource(model: Model, action: (ManagedNodeDataSource, CryptoSystem) -> Unit) {

        if (model is PostchainModel && model.blockchainConfiguration is ManagedDataSourceAware) {

            action(model.blockchainConfiguration.dataSource, model.postchainContext.cryptoSystem)
        }
    }

    private fun getErrors(request: Request): Response {
        val model = model(request)
        val errors = JsonArray()
        (checkDiagnosticError(model.blockchainRid) ?: listOf()).forEach {
            when (it) {
                is ErrorValue -> errors.add(prettyGson.toJsonTree(it))
                else -> errors.add(it.toString())
            }
        }
        return Response(OK).with(prettyJsonBody of errors)
    }

    private fun getMetadata(request: Request): Response {
        val model = model(request)
        val metadata = model.getMetadata()
        return Response(OK).with(metadataBody of metadata)
    }

    val handler = routes(
            basePath bind app
    )

    private val chainModelKey = RequestKey.required<ChainModel>("chainModel")
    private val blockchainRidKey = RequestKey.required<BlockchainRid>("blockchainRid")
    private val modelKey = RequestKey.optional<Model>("model")

    val requestLogContext = Filter { next ->
        { request: Request ->
            withLoggingContext(
                    "requestSource" to "rest",
                    "requestRestComponent" to getEndpointComponent(request.uri.path),
                    "requestRestMethod" to request.method.name
            ) {
                next(request)
            }
        }
    }

    val server = ServerFilters.Cors(CorsPolicy(
            OriginPolicy.AllowAll(),
            listOf("Content-Type", "Accept", "X-Accept-Query-Response-Signature"),
            listOf(GET, POST, OPTIONS),
            credentials = false,
            exposedHeaders = listOf(DATA_TRUNCATED_HEADER, TRANSACTION_TIMESTAMP, BLOCK_HEIGHT_HEADER, QUERY_RESPONSE_SIGNATURE)
    ))
            .then(requestLogContext)
            .then(Filter { next ->
                { request ->
                    try {
                        next(request)
                    } catch (e: Exception) {
                        onError(e, request)
                    }
                }
            })
            .then(ServerFilters.CatchLensFailure { request, lensFailure ->
                logger.info { "Bad request: ${lensFailure.message}" }
                errorResponse(request, BAD_REQUEST, lensFailure.failures.joinToString("; "))
            })
            .then(handler)
            .asServer(NettyWithCustomWorkerGroup(
                    listenPort,
                    if (gracefulShutdown) ServerConfig.StopMode.Graceful(Duration.ofSeconds(5)) else ServerConfig.StopMode.Immediate,
                    MultiThreadIoEventLoopGroup(requestConcurrency, ThreadFactoryBuilder().setNameFormat("REST-API-%d").build(), NioIoHandler.newFactory()),
                    maxRequestBodySize
            ))
            .start().also {
                logger.info { "Rest API is listening on port ${it.port()} and is attached on $basePath/" }
                logger.info { "Rest API config: requestConcurrency=$requestConcurrency, requestConcurrencyLocal=$requestConcurrencyLocal, requestConcurrencyExternal=$requestConcurrencyExternal, chainRequestConcurrency=$chainRequestConcurrency, containerRequestConcurrency=$containerRequestConcurrency" }
            }

    private fun onError(error: Exception, request: Request): Response {
        return when (error) {
            is NotFoundError -> {
                logger.info { "Not found: ${error.message}" }
                errorResponse(request, NOT_FOUND, error.message!!)
            }

            is IllegalArgumentException -> {
                logger.info { "Illegal argument: ${error.message}" }
                errorResponse(request, BAD_REQUEST, error.message!!)
            }

            is UnknownOperation -> {
                logger.info { error.message }
                errorResponse(request, BAD_REQUEST, error.message!!, errorCode = Errors.OPERATION_NOT_FOUND)
            }

            is UnknownQuery -> {
                logger.info { error.message }
                errorResponse(request, BAD_REQUEST, error.message!!, errorCode = Errors.QUERY_NOT_FOUND)
            }

            is UserMistake -> {
                logger.info { "User mistake: ${error.message}" }
                errorResponse(request, BAD_REQUEST, error.message!!)
            }

            is InvalidTnxException -> {
                logger.info { "Invalid transaction: ${error.message}" }
                errorResponse(request, BAD_REQUEST, error.message!!)
            }

            is UnauthorizedException -> {
                logger.info { "Unauthorized: ${error.message}" }
                errorResponse(request, UNAUTHORIZED, error.message!!)
            }

            is ForbiddenException -> {
                logger.info { "Forbidden: ${error.message}" }
                errorResponse(request, FORBIDDEN, error.message!!)
            }

            is DuplicateException -> {
                logger.info { "Duplicate: ${error.message}" }
                errorResponse(request, CONFLICT, error.message!!)
            }

            is NotSupported -> {
                logger.info { "Not supported: ${error.message}" }
                errorResponse(request, FORBIDDEN, error.message!!)
            }

            is PmEngineIsAlreadyClosed -> {
                logger.info { "Engine is closed: ${error.message}" }
                errorResponse(request, SERVICE_UNAVAILABLE, error.message!!)
            }

            is UnavailableException -> {
                logger.info { "Unavailable: ${error.message}" }
                transformErrorResponseFromDiagnostics(request, SERVICE_UNAVAILABLE, error.message ?: "Unknown error")
            }

            is TimeoutException -> {
                logger.info { "Timeout: ${error.message}" }
                transformErrorResponseFromDiagnostics(request, INTERNAL_SERVER_ERROR, error.message ?: "Unknown timeout")
            }

            else -> {
                logger.warn(error) { "Unexpected exception: $error" }
                transformErrorResponseFromDiagnostics(request, INTERNAL_SERVER_ERROR, "Unknown error")
            }
        }
    }

    private fun transformErrorResponseFromDiagnostics(request: Request, status: Status, error: String): Response =
            modelKey(request)?.let { checkDiagnosticError(it.blockchainRid) }?.let { errorMessage ->
                errorResponse(request, INTERNAL_SERVER_ERROR, errorMessage.toString())
            } ?: errorResponse(request, status, error)

    private fun checkDiagnosticError(blockchainRid: BlockchainRid): List<Any?>? =
            if (nodeDiagnosticContext.hasBlockchainErrors(blockchainRid)) {
                nodeDiagnosticContext.blockchainErrorQueue(blockchainRid).value
            } else {
                null
            }

    private fun errorResponse(request: Request, status: Status, errorMessage: String, errorCode: Errors? = null): Response =
            Response(status).with(
                    errorBody.outbound(request) of ErrorBody(errorMessage, errorCode?.name)
            )

    private fun model(request: Request): Model = modelKey(request) ?: throw invalidBlockchainRid()

    private fun invalidBlockchainRid() = LensFailure(listOf(
            Invalid(Meta(true, "path", ParamMeta.StringParam, BLOCKCHAIN_RID, "Invalid blockchainRID. Expected 64 hex digits [0-9a-fA-F]", mapOf()))))

    private fun resolveBlockchain(ref: BlockchainRef): BlockchainRid = when (ref) {
        is BlockchainRidRef -> ref.rid

        is BlockchainIidRef -> bridByIID[ref.iid]
                ?: throw NotFoundError("Can't find blockchain with chain Iid: ${ref.iid} in DB. Did you add this BC to the node?")
    }

    private fun parseQuery(gtxQuery: Gtv): GtxQuery {
        val queryDict = gtxQuery.asDict()
        val type = queryDict[QUERY_TYPE] ?: throw UserMistake("Missing query type")
        val args = gtv(queryDict.filterKeys { key -> key != QUERY_TYPE } + (NON_STRICT_QUERY_ARGUMENT to gtv(true)))
        return GtxQuery(type.asString(), args)
    }

    private fun extractGetQuery(queryMap: Map<String, List<String?>>): GtxQuery {
        val type = queryMap[QUERY_TYPE]?.singleOrNull() ?: throw UserMistake("Missing query type")
        val args = if (queryMap.size == 1) gtv(mapOf())
        else queryMap[QUERY_ARGS]?.singleOrNull()?.let {
            try {
                GtvDecoder.decodeGtv(it.hexStringToByteArray())
            } catch (e: GtvException) {
                logger.debug { "Invalid GTV data in GET /query_gtv: $e" }
                throw IllegalArgumentException("Invalid GTV data")
            }
        } ?: gtv(queryMap.filterKeys { it != QUERY_TYPE }.mapValues {
            val paramValue = requireNotNull(it.value.single())
            if (paramValue == "true" || paramValue == "false") {
                gtv(paramValue.toBoolean())
            } else if (paramValue.toLongOrNull() != null) {
                gtv(paramValue.toLong())
            } else {
                gtv(paramValue)
            }
        } + (NON_STRICT_QUERY_ARGUMENT to gtv(true)))
        return GtxQuery(type, args)
    }

    private fun <T> runTxActionOnModel(model: Model, txRid: TxRid, txAction: (Model, TxRid) -> T?): T =
            txAction(model, txRid)
                    ?: throw NotFoundError("Can't find transaction with RID: $txRid")

    private fun chainModel(blockchainRid: BlockchainRid, container: String): Pair<ChainModel, Semaphore?> {
        return if (container.isEmpty()) {
            models.asSequence().firstOrNull { it.key.first == blockchainRid }?.value
                    ?: throw NotFoundError("Can't find blockchain with blockchainRID: $blockchainRid")
        } else {
            models[blockchainRid to container]
                    ?: throw NotFoundError("Can't find blockchain with blockchainRID: $blockchainRid in the container '$container'")
        }
    }

    /**
     * We allow two different syntaxes for finding the blockchain.
     * 1. provide BC RID
     * 2. provide Chain IID (should not be used in production, since to ChainIid could be anything).
     */
    private fun parseBlockchainRid(s: String): BlockchainRef? {
        val blockchainRidMatch = blockchainRidPattern.matchEntire(s)
        val chainIidMatch = chainIidPattern.matchEntire(s)
        return if (blockchainRidMatch != null) {
            BlockchainRidRef(BlockchainRid.buildFromHex(blockchainRidMatch.groupValues[1]))
        } else if (chainIidMatch != null) {
            BlockchainIidRef(chainIidMatch.groupValues[1].toLong())
        } else {
            null
        }
    }

    override fun close() {
        server.close()
        System.gc()
    }

    /** Extract the component from endpoint path: /api/v1/<component>/.* */
    private fun getEndpointComponent(path: String): String? {
        val apiEndpointOffset = basePath.length + 1
        if (path.length > apiEndpointOffset) {
            return path.substring(apiEndpointOffset).split("/")[0]
        }
        return null
    }

    private fun maybeTryAcquireSemaphore(semaphores: List<Pair<Semaphore?, () -> String>>, request: Request, next: () -> Response): Response {
        if (semaphores.isNotEmpty()) {
            val (semaphore, unavailableMessage) = semaphores.first()
            return if (semaphore != null) {
                if (semaphore.tryAcquire()) {
                    try {
                        maybeTryAcquireSemaphore(semaphores.drop(1), request, next)
                    } finally {
                        semaphore.release()
                    }
                } else {
                    errorResponse(request, SERVICE_UNAVAILABLE, unavailableMessage())
                }
            } else {
                maybeTryAcquireSemaphore(semaphores.drop(1), request, next)
            }
        }
        return next()
    }
}

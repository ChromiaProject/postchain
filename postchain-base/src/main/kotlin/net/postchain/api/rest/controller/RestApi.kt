// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.controller

import mu.KLogging
import mu.withLoggingContext
import net.postchain.api.rest.BlockchainIidRef
import net.postchain.api.rest.BlockchainRef
import net.postchain.api.rest.BlockchainRidRef
import net.postchain.api.rest.Empty
import net.postchain.api.rest.ErrorBody
import net.postchain.api.rest.Version
import net.postchain.api.rest.batchQueriesBody
import net.postchain.api.rest.beforeHeightQuery
import net.postchain.api.rest.beforeTimeQuery
import net.postchain.api.rest.binaryBody
import net.postchain.api.rest.blockBody
import net.postchain.api.rest.blockHeightBody
import net.postchain.api.rest.blockRidPath
import net.postchain.api.rest.blocksBody
import net.postchain.api.rest.configurationInBody
import net.postchain.api.rest.configurationOutBody
import net.postchain.api.rest.emptyBody
import net.postchain.api.rest.errorBody
import net.postchain.api.rest.gtvJsonBody
import net.postchain.api.rest.gtxQueriesBody
import net.postchain.api.rest.heightPath
import net.postchain.api.rest.heightQuery
import net.postchain.api.rest.limitQuery
import net.postchain.api.rest.model.TxRid
import net.postchain.api.rest.nodeStatusBody
import net.postchain.api.rest.nodeStatusesBody
import net.postchain.api.rest.nullBody
import net.postchain.api.rest.prettyJsonBody
import net.postchain.api.rest.proofBody
import net.postchain.api.rest.statusBody
import net.postchain.api.rest.stringsBody
import net.postchain.api.rest.textBody
import net.postchain.api.rest.txBody
import net.postchain.api.rest.txInfoBody
import net.postchain.api.rest.txInfosBody
import net.postchain.api.rest.txRidPath
import net.postchain.api.rest.txsQuery
import net.postchain.api.rest.versionBody
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.core.PmEngineIsAlreadyClosed
import net.postchain.debug.JsonNodeDiagnosticContext
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvException
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvType
import net.postchain.gtv.gtvToJSON
import net.postchain.gtv.make_gtv_gson
import net.postchain.gtx.GtxQuery
import net.postchain.gtx.NON_STRICT_QUERY_ARGUMENT
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Filter
import org.http4k.core.Method.GET
import org.http4k.core.Method.OPTIONS
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.RequestContexts
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CONFLICT
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SERVICE_UNAVAILABLE
import org.http4k.core.queries
import org.http4k.core.then
import org.http4k.core.toParametersMap
import org.http4k.core.with
import org.http4k.filter.AllowAll
import org.http4k.filter.CorsPolicy
import org.http4k.filter.OriginPolicy
import org.http4k.filter.ServerFilters
import org.http4k.format.auto
import org.http4k.lens.ContentNegotiation
import org.http4k.lens.Header
import org.http4k.lens.Invalid
import org.http4k.lens.LensFailure
import org.http4k.lens.Meta
import org.http4k.lens.ParamMeta
import org.http4k.lens.RequestContextKey
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.Netty
import org.http4k.server.ServerConfig
import org.http4k.server.asServer
import java.io.Closeable
import java.nio.ByteBuffer
import java.time.Duration

/**
 * Implements the REST API.
 */
class RestApi(
        private val listenPort: Int,
        val basePath: String,
        private val nodeDiagnosticContext: NodeDiagnosticContext = JsonNodeDiagnosticContext(),
        gracefulShutdown: Boolean = true
) : Modellable, Closeable {

    companion object : KLogging() {
        const val REST_API_VERSION = 1

        private const val MAX_NUMBER_OF_BLOCKS_PER_REQUEST = 100
        private const val DEFAULT_ENTRY_RESULTS_REQUEST = 25
        private const val MAX_NUMBER_OF_TXS_PER_REQUEST = 600

        private val blockchainRidPattern = Regex("/[^/]+/([0-9a-fA-F]+).*")
        private val chainIidPattern = Regex("/[^/]+/iid_([0-9]+).*")
    }

    private val models = mutableMapOf<BlockchainRid, ChainModel>()
    private val bridByIID = mutableMapOf<Long, BlockchainRid>()

    override fun attachModel(blockchainRid: BlockchainRid, chainModel: ChainModel) {
        models[blockchainRid] = chainModel
        bridByIID[chainModel.chainIID] = blockchainRid
    }

    override fun detachModel(blockchainRid: BlockchainRid) {
        val model = models.remove(blockchainRid)
        if (model != null) {
            bridByIID.remove(model.chainIID)
        } else throw ProgrammerMistake("Blockchain $blockchainRid not attached")
    }

    override fun retrieveModel(blockchainRid: BlockchainRid): ChainModel? = models[blockchainRid] as? Model

    fun actualPort(): Int = server.port()

    private val gtvGson = make_gtv_gson()

    private val app = routes(
            "/" bind static(ResourceLoader.Classpath("/restapi-root")),
            "/apidocs" bind static(ResourceLoader.Classpath("/restapi-docs")),

            "/tx/{blockchainRid}" bind POST to ::postTransaction,
            "/tx/{blockchainRid}/{txRid}" bind GET to ::getTransaction,
            "/transactions/{blockchainRid}/{txRid}" bind GET to ::getTransactionInfo,
            "/transactions/{blockchainRid}" bind GET to ::getTransactionsInfo,
            "/tx/{blockchainRid}/{txRid}/confirmationProof" bind GET to ::getConfirmationProof,
            "/tx/{blockchainRid}/{txRid}/status" bind GET to ::getTransactionStatus,

            "/blocks/{blockchainRid}" bind GET to ::getBlocks,
            "/blocks/{blockchainRid}/{blockRid}" bind GET to ::getBlock,
            "/blocks/{blockchainRid}/height/{height}" bind GET to ::getBlockByHeight,

            "/query/{blockchainRid}" bind GET to ::getQuery,
            "/query/{blockchainRid}" bind POST to ::postQuery,
            "/batch_query/{blockchainRid}" bind POST to ::batchQuery,
            // Direct query. That should be used as example: <img src="http://node/dquery/brid?type=get_picture&id=4555" />
            "/dquery/{blockchainRid}" bind GET to ::directQuery,
            "/query_gtx/{blockchainRid}" bind POST to ::queryGtx,
            "/query_gtv/{blockchainRid}" bind POST to ::queryGtv,

            "/node/{blockchainRid}/my_status" bind GET to ::getNodeStatus,
            "/node/{blockchainRid}/statuses" bind GET to ::getNodeStatuses,
            "/_debug" bind GET to ::getDebug,
            "/brid/{blockchainRid}" bind GET to ::getBlockchainRid,
            "/blockchain/{blockchainRid}/height" bind GET to ::getCurrentHeight,

            "/config/{blockchainRid}" bind GET to ::getBlockchainConfiguration,
            "/config/{blockchainRid}" bind POST to ::validateBlockchainConfiguration,

            "/version" bind GET to ::getVersion
    )

    private fun postTransaction(request: Request): Response {
        val model = model(request)
        val tx = ContentNegotiation.auto(txBody, binaryBody)(request)
        model.postTransaction(tx)
        return Response(OK).with(emptyBody.outbound(request) of Empty)
    }

    private fun getTransaction(request: Request): Response {
        val tx = runTxActionOnModel(model(request), txRidPath(request)) { model, txRID ->
            model.getTransaction(txRID)
        }
        return Response(OK).with(ContentNegotiation.auto(txBody, binaryBody).outbound(request) of tx)
    }

    private fun getTransactionInfo(request: Request): Response {
        val txInfo = runTxActionOnModel(model(request), txRidPath(request)) { model, txRID ->
            model.getTransactionInfo(txRID)
        }
        return Response(OK).with(txInfoBody of txInfo)
    }

    private fun getTransactionsInfo(request: Request): Response {
        val model = model(request)
        val limit = limitQuery(request)?.coerceIn(0, MAX_NUMBER_OF_TXS_PER_REQUEST)
                ?: DEFAULT_ENTRY_RESULTS_REQUEST
        val beforeTime = beforeTimeQuery(request) ?: Long.MAX_VALUE
        val txInfos = model.getTransactionsInfo(beforeTime, limit)
        return Response(OK).with(txInfosBody of txInfos)
    }

    private fun getConfirmationProof(request: Request): Response {
        val proof = runTxActionOnModel(model(request), txRidPath(request)) { model, txRID ->
            model.getConfirmationProof(txRID)
        }
        return Response(OK).with(proofBody of proof)
    }

    private fun getTransactionStatus(request: Request): Response {
        val status = runTxActionOnModel(model(request), txRidPath(request)) { model, txRID ->
            model.getStatus(txRID)
        }
        return Response(OK).with(statusBody of status)
    }

    private fun getBlocks(request: Request): Response {
        val model = model(request)
        val beforeTime = beforeTimeQuery(request)
        val beforeHeight = beforeHeightQuery(request)
        if (beforeTime != null && beforeHeight != null) {
            throw UserMistake("Cannot specify both before-time and before-height")
        }
        val limit = limitQuery(request)?.coerceIn(0, MAX_NUMBER_OF_BLOCKS_PER_REQUEST)
                ?: DEFAULT_ENTRY_RESULTS_REQUEST
        val txHashesOnly = txsQuery(request) != true
        val blocks = if (beforeHeight != null) {
            model.getBlocksBeforeHeight(beforeHeight, limit, txHashesOnly)
        } else {
            model.getBlocks(beforeTime ?: Long.MAX_VALUE, limit, txHashesOnly)
        }
        return Response(OK).with(blocksBody of blocks)
    }

    private fun getBlock(request: Request): Response {
        val model = model(request)
        val blockRID = blockRidPath(request)
        val txHashesOnly = txsQuery(request) != true
        val block = model.getBlock(blockRID, txHashesOnly)
        return if (block != null) {
            Response(OK).with(blockBody.outbound(request) of block)
        } else {
            Response(OK).with(nullBody.outbound(request) of Unit)
        }
    }

    private fun getBlockByHeight(request: Request): Response {
        val model = model(request)
        val height = heightPath(request)
        val txHashesOnly = txsQuery(request) != true
        val block = model.getBlock(height, txHashesOnly)
        return if (block != null) {
            Response(OK).with(blockBody.outbound(request) of block)
        } else {
            Response(OK).with(nullBody.outbound(request) of Unit)
        }
    }

    private fun getQuery(request: Request): Response {
        val model = model(request)
        val query = extractGetQuery(request.uri.queries().toParametersMap())
        val queryResult = model.query(query)
        return Response(OK).with(gtvJsonBody of queryResult)
    }

    private fun postQuery(request: Request): Response {
        val model = model(request)
        val gtxQuery = gtvJsonBody(request)
        val query = parseQuery(gtxQuery)
        val queryResult = model.query(query)
        return Response(OK).with(gtvJsonBody of queryResult)
    }

    private fun batchQuery(request: Request): Response {
        val model = model(request)
        val queries: List<Gtv> = batchQueriesBody(request)
        val responses: List<String> = queries.map { gtxQuery ->
            val query = parseQuery(gtxQuery)
            val queryResult = model.query(query)
            gtvToJSON(queryResult, gtvGson)
        }
        return Response(OK).with(stringsBody of responses)
    }

    private fun directQuery(request: Request): Response {
        val model = model(request)
        val query = extractGetQuery(request.uri.queries().toParametersMap())
        val array = model.query(query).asArray()
        if (array.size < 2) {
            throw UserMistake("Response should have two parts: content-type and content")
        }
        // first element is content-type
        val contentType = array[0].asString()
        val content = array[1]
        return when (content.type) {
            GtvType.STRING -> Response(OK)
                    .with(Header.CONTENT_TYPE.of(ContentType(contentType)))
                    .body(content.asString())

            GtvType.BYTEARRAY -> Response(OK)
                    .with(Header.CONTENT_TYPE.of(ContentType(contentType)))
                    .body(Body.invoke(ByteBuffer.wrap(content.asByteArray())))

            else -> throw UserMistake("Unexpected content")
        }
    }

    private fun queryGtx(request: Request): Response {
        val model = model(request)
        val queries = gtxQueriesBody(request)
        val responses = queries.queries.map {
            val gtxQuery = try {
                GtxQuery.decode(it.hexStringToByteArray())
            } catch (e: GtvException) {
                throw IllegalArgumentException(e.message ?: "")
            }
            GtvEncoder.encodeGtv(model.query(gtxQuery)).toHex()
        }
        return Response(OK).with(stringsBody of responses)
    }

    private fun queryGtv(request: Request): Response {
        val model = model(request)
        val query = binaryBody(request)
        val gtvQuery = try {
            GtxQuery.decode(query)
        } catch (e: GtvException) {
            throw IllegalArgumentException(e.message ?: "")
        }
        val response = model.query(gtvQuery)
        return Response(OK).with(binaryBody of GtvEncoder.encodeGtv(response))
    }

    private fun getNodeStatus(request: Request): Response {
        val model = model(request)
        return Response(OK).with(nodeStatusBody of model.nodeStatusQuery())
    }

    private fun getNodeStatuses(request: Request): Response {
        val model = model(request)
        return Response(OK).with(nodeStatusesBody of model.nodePeersStatusQuery())
    }

    private fun getDebug(request: Request): Response {
        val debugInfo = models.values
                .filterIsInstance(Model::class.java)
                .firstOrNull()
                ?.debugQuery(null)
                ?: throw NotFoundError("There are no running chains")
        return Response(OK).with(prettyJsonBody of debugInfo)
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

    private fun getBlockchainConfiguration(request: Request): Response {
        val model = model(request)
        val height = heightQuery(request)
        val configuration = model.getBlockchainConfiguration(height)
                ?: throw UserMistake("Failed to find configuration")
        return Response(OK).with(configurationOutBody.outbound(request) of configuration)
    }

    private fun validateBlockchainConfiguration(request: Request): Response {
        val model = model(request)
        val configuration = configurationInBody(request)

        try {
            model.validateBlockchainConfiguration(configuration)
        } catch (e: UserMistake) {
            throw e
        } catch (e: Exception) {
            throw UserMistake("Invalid configuration: ${e.message}", e)
        }
        return Response(OK).with(emptyBody.outbound(request) of Empty)
    }

    private fun getVersion(request: Request): Response = Response(OK).with(
            versionBody of Version(REST_API_VERSION)
    )

    val handler = routes(
            basePath bind app
    )

    private val contexts = RequestContexts()
    private val modelKey = RequestContextKey.optional<Model>(contexts)

    val server = ServerFilters.InitialiseRequestContext(contexts)
            .then(ServerFilters.Cors(
                    CorsPolicy(OriginPolicy.AllowAll(), listOf("Content-Type", "Accept"), listOf(GET, POST, OPTIONS), credentials = false)))
            .then(ServerFilters.GZip())
            .then(Filter { next ->
                { request ->
                    if (logger.isDebugEnabled) {
                        val requestInfo = "[${request.source?.address ?: "(unknown)"}] ${request.method} ${request.uri.path}"
                        // Assuming content-type is correctly set we will avoid logging binary request bodies
                        if (Header.CONTENT_TYPE(request)?.equalsIgnoringDirectives(ContentType.OCTET_STREAM) != true
                                && (request.body.length ?: 0) > 0) {
                            logger.debug { "$requestInfo with body: ${String(request.body.payload.array())}" }
                        } else {
                            val queryString = request.uri.query
                            logger.debug("$requestInfo${if (queryString.isBlank()) "" else "?$queryString"}")
                        }
                    }
                    val response = next(request)
                    if (logger.isDebugEnabled) {
                        // Assuming content-type is correctly set we will avoid logging binary response bodies
                        if (Header.CONTENT_TYPE(response)?.equalsIgnoringDirectives(ContentType.OCTET_STREAM) != true
                                && (response.body.length ?: 0) > 0) {
                            logger.debug("Response body: ${String(response.body.payload.array())}")
                        }
                    }
                    response
                }
            })
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
                Response(BAD_REQUEST).with(
                        errorBody.outbound(request) of ErrorBody(lensFailure.failures.joinToString("; "))
                )
            })
            .then(Filter { next ->
                { request ->
                    val blockchainRef = parseBlockchainRid(request.uri.path)
                    if (blockchainRef != null) {
                        val blockchainRid = resolveBlockchain(blockchainRef)
                        val chainModel = chainModel(blockchainRid)
                        withLoggingContext(
                                BLOCKCHAIN_RID_TAG to blockchainRid.toHex(),
                                CHAIN_IID_TAG to chainModel.chainIID.toString()) {
                            when (chainModel) {
                                is Model -> {
                                    logger.trace { "Local REST API model found: $chainModel" }
                                    next(request.with(modelKey of chainModel))
                                }

                                is ExternalModel -> {
                                    logger.trace { "External REST API model found: $chainModel" }
                                    chainModel(request)
                                }
                            }
                        }
                    } else {
                        next(request)
                    }
                }
            })
            .then(handler)
            .asServer(Netty(listenPort,
                    if (gracefulShutdown) ServerConfig.StopMode.Graceful(Duration.ofSeconds(15)) else ServerConfig.StopMode.Immediate))
            .start().also {
                logger.info { "Rest API listening on port ${it.port()} and were given $listenPort" }
                logger.info { "Rest API attached on $basePath/" }
            }

    private fun onError(error: Exception, request: Request): Response {
        return when (error) {
            is NotFoundError -> {
                logger.debug { "NotFound: ${error.message}" }
                transformErrorResponseFromDiagnostics(request, NOT_FOUND, error)
            }

            is LensFailure -> {
                logger.debug { "BadFormat: ${error.message}" }
                errorResponse(request, BAD_REQUEST, error.message!!)
            }

            is IllegalArgumentException -> {
                logger.debug { "IllegalArgument: ${error.message}" }
                errorResponse(request, BAD_REQUEST, error.message!!)
            }

            is UserMistake -> {
                logger.debug { "UserMistake: ${error.message}" }
                errorResponse(request, BAD_REQUEST, error.message!!)
            }

            is InvalidTnxException -> {
                errorResponse(request, BAD_REQUEST, error.message!!)
            }

            is DuplicateTnxException -> {
                errorResponse(request, CONFLICT, error.message!!)
            }

            is NotSupported -> {
                errorResponse(request, FORBIDDEN, error.message!!)
            }

            is UnavailableException -> {
                transformErrorResponseFromDiagnostics(request, SERVICE_UNAVAILABLE, error)
            }

            is PmEngineIsAlreadyClosed -> {
                errorResponse(request, SERVICE_UNAVAILABLE, error.message!!)
            }

            else -> {
                logger.warn(error) { "Unexpected exception: $error" }
                transformErrorResponseFromDiagnostics(request, INTERNAL_SERVER_ERROR, error)
            }
        }
    }

    private fun transformErrorResponseFromDiagnostics(request: Request, status: Status, error: Exception): Response =
            modelKey(request)?.let { checkDiagnosticError(it.blockchainRid) }?.let { errorMessage ->
                errorResponse(request, INTERNAL_SERVER_ERROR, errorMessage)
            } ?: errorResponse(request, status, error.message ?: "Unknown error")

    private fun checkDiagnosticError(blockchainRid: BlockchainRid): String? =
            if (nodeDiagnosticContext.hasBlockchainErrors(blockchainRid)) {
                nodeDiagnosticContext.blockchainErrorQueue(blockchainRid).value.toString()
            } else {
                null
            }

    private fun errorResponse(request: Request, status: Status, errorMessage: String): Response =
            Response(status).with(
                    errorBody.outbound(request) of ErrorBody(errorMessage)
            )

    private fun model(request: Request): Model = modelKey(request) ?: throw LensFailure(listOf(
            Invalid(Meta(true, "path", ParamMeta.StringParam, "blockchainRid", "Invalid blockchainRID. Expected 64 hex digits [0-9a-fA-F]"))))

    private fun resolveBlockchain(ref: BlockchainRef): BlockchainRid = when (ref) {
        is BlockchainRidRef -> ref.rid

        is BlockchainIidRef -> bridByIID[ref.iid]
                ?: throw NotFoundError("Can't find blockchain with chain Iid: ${ref.iid} in DB. Did you add this BC to the node?")
    }

    private fun parseQuery(gtxQuery: Gtv): GtxQuery {
        val queryDict = gtxQuery.asDict()
        val type = queryDict["type"] ?: throw UserMistake("Missing query type")
        val args = gtv(queryDict.filterKeys { key -> key != "type" } + (NON_STRICT_QUERY_ARGUMENT to gtv(true)))
        return GtxQuery(type.asString(), args)
    }

    private fun extractGetQuery(queryMap: Map<String, List<String?>>): GtxQuery {
        val type = queryMap["type"]?.singleOrNull() ?: throw UserMistake("Missing query type")
        val args = queryMap.filterKeys { it != "type" }.mapValues {
            val paramValue = requireNotNull(it.value.single())
            if (paramValue == "true" || paramValue == "false") {
                gtv(paramValue.toBoolean())
            } else if (paramValue.toLongOrNull() != null) {
                gtv(paramValue.toLong())
            } else {
                gtv(paramValue)
            }
        } + (NON_STRICT_QUERY_ARGUMENT to gtv(true))
        return GtxQuery(type, gtv(args))
    }

    private fun <T> runTxActionOnModel(model: Model, txRid: TxRid, txAction: (Model, TxRid) -> T?): T =
            txAction(model, txRid)
                    ?: throw NotFoundError("Can't find tx with hash $txRid")

    private fun chainModel(blockchainRid: BlockchainRid): ChainModel {
        val chainModel = models[blockchainRid]
                ?: throw NotFoundError("Can't find blockchain with blockchainRID: $blockchainRid")
        if (!chainModel.live) throw UnavailableException("Blockchain is unavailable")
        return chainModel
    }

    /**
     * We allow two different syntax for finding the blockchain.
     * 1. provide BC RID
     * 2. provide Chain IID (should not be used in production, since to ChainIid could be anything).
     */
    private fun parseBlockchainRid(path: String): BlockchainRef? {
        val localPath = path.substring(basePath.length)
        val blockchainRidMatch = blockchainRidPattern.matchEntire(localPath)
        val chainIidMatch = chainIidPattern.matchEntire(localPath)
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
        System.runFinalization()
    }
}

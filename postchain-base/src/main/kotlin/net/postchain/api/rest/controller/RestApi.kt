// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.controller

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kong.unirest.HttpMethod
import mu.KLogging
import net.postchain.api.rest.controller.HttpHelper.Companion.ACCESS_CONTROL_ALLOW_HEADERS
import net.postchain.api.rest.controller.HttpHelper.Companion.ACCESS_CONTROL_ALLOW_METHODS
import net.postchain.api.rest.controller.HttpHelper.Companion.ACCESS_CONTROL_ALLOW_ORIGIN
import net.postchain.api.rest.controller.HttpHelper.Companion.ACCESS_CONTROL_REQUEST_HEADERS
import net.postchain.api.rest.controller.HttpHelper.Companion.ACCESS_CONTROL_REQUEST_METHOD
import net.postchain.api.rest.controller.HttpHelper.Companion.PARAM_BLOCKCHAIN_RID
import net.postchain.api.rest.controller.HttpHelper.Companion.PARAM_HASH_HEX
import net.postchain.api.rest.controller.HttpHelper.Companion.PARAM_HEIGHT
import net.postchain.api.rest.controller.HttpHelper.Companion.SUBQUERY
import net.postchain.api.rest.json.JsonFactory
import net.postchain.api.rest.model.ApiTx
import net.postchain.api.rest.model.TxRID
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.core.PmEngineIsAlreadyClosed
import net.postchain.debug.JsonNodeDiagnosticContext
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.GtvType
import net.postchain.gtv.gtvToJSON
import net.postchain.gtv.gtvml.GtvMLEncoder
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.make_gtv_gson
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtx.GtxQuery
import spark.Request
import spark.Response
import spark.Service
import java.io.IOException

/**
 * Contains information on the rest API, such as network parameters and available queries
 */
class RestApi(
        private val listenPort: Int,
        private val basePath: String,
        private val tlsCertificate: String? = null,
        private val tlsCertificatePassword: String? = null,
        private val nodeDiagnosticContext: NodeDiagnosticContext = JsonNodeDiagnosticContext()
) : Modellable {

    private val MAX_NUMBER_OF_BLOCKS_PER_REQUEST = 100
    private val DEFAULT_ENTRY_RESULTS_REQUEST = 25
    private val MAX_NUMBER_OF_TXS_PER_REQUEST = 600

    companion object : KLogging() {
        const val JSON_CONTENT_TYPE = "application/json"
        const val OCTET_CONTENT_TYPE = "application/octet-stream"
        const val XML_CONTENT_TYPE = "text/xml"
    }

    private val http = Service.ignite()!!
    private val gson = JsonFactory.makeJson()
    private val gtvGson = make_gtv_gson()
    private val models = mutableMapOf<String, ChainModel>()
    private val bridByIID = mutableMapOf<Long, String>()

    init {
        buildErrorHandler(http)
        buildRouter(http)
        logger.info { "Rest API listening on port ${actualPort()} and were given $listenPort" }
        logger.info { "Rest API attached on $basePath/" }
    }

    override fun attachModel(blockchainRid: String, chainModel: ChainModel) {
        val brid = blockchainRid.uppercase()
        models[brid] = chainModel
        bridByIID[chainModel.chainIID] = brid
    }

    override fun detachModel(blockchainRid: String) {
        val brid = blockchainRid.uppercase()
        val model = models.remove(brid)
        if (model != null) {
            bridByIID.remove(model.chainIID)
        } else throw ProgrammerMistake("Blockchain $blockchainRid not attached")
    }

    override fun retrieveModel(blockchainRid: String): ChainModel? {
        return models[blockchainRid.uppercase()] as? Model
    }

    fun actualPort(): Int {
        return http.port()
    }

    private fun buildErrorHandler(http: Service) {
        http.exception(NotFoundError::class.java) { error, request, response ->
            logger.warn("NotFound: ${error.message}")
            response.status(404)
            transformErrorResponseFromDiagnostics(request, response, error)
        }

        http.exception(BadFormatError::class.java) { error, _, response ->
            logger.warn("BadFormat: ${error.message}")
            response.status(400)
            setErrorResponseBody(response, error)
        }

        http.exception(UserMistake::class.java) { error, _, response ->
            logger.warn("UserMistake: ${error.message}")
            response.status(400)
            setErrorResponseBody(response, error)
        }

        http.exception(InvalidTnxException::class.java) { error, _, response ->
            response.status(400)
            setErrorResponseBody(response, error)
        }

        http.exception(DuplicateTnxException::class.java) { error, _, response ->
            response.status(409) // Conflict
            setErrorResponseBody(response, error)
        }

        http.exception(NotSupported::class.java) { error, _, response ->
            response.status(403) // Forbidden
            setErrorResponseBody(response, error)
        }

        http.exception(UnavailableException::class.java) { error, request, response ->
            response.status(503) // Service unavailable
            transformErrorResponseFromDiagnostics(request, response, error)
        }

        http.exception(PmEngineIsAlreadyClosed::class.java) { error, _, response ->
            response.status(503) // Service unavailable
            setErrorResponseBody(response, error)
        }

        http.exception(Exception::class.java) { error, request, response ->
            logger.error("Exception: $error", error)
            response.status(500)
            transformErrorResponseFromDiagnostics(request, response, error)
        }

        http.notFound { _, _ -> toJson(UserMistake("Not found")) }
    }

    private fun transformErrorResponseFromDiagnostics(request: Request, response: Response, error: Exception) {
        val blockchainRid = if (request.params(PARAM_BLOCKCHAIN_RID) != null) checkBlockchainRID(request) else null
        blockchainRid?.let { checkDiagnosticError(BlockchainRid.buildFromHex(blockchainRid)) }?.let { errorBody ->
            response.status(500)
            response.type(JSON_CONTENT_TYPE)
            response.body(gson.toJson(errorBody))
        } ?: setErrorResponseBody(response, error)
    }

    private fun checkDiagnosticError(blockchainRid: BlockchainRid): JsonObject? {
        if (!nodeDiagnosticContext.hasBlockchainErrors(blockchainRid)) return null
        return nodeDiagnosticContext.blockchainErrorQueue(blockchainRid).let {
            JsonObject().apply { addProperty("error", it.value.toString()) }
        }
    }

    private fun setErrorResponseBody(response: Response, error: Exception) {
        if (response.type() == OCTET_CONTENT_TYPE) {
            response.raw().outputStream.apply {
                write(GtvEncoder.encodeGtv(gtv(error.message ?: "Unknown error")))
                flush()
            }
        } else {
            response.type(JSON_CONTENT_TYPE)
            response.body(toJson(error))
        }
    }

    private fun buildRouter(http: Service) {
        http.port(listenPort)
        if (tlsCertificate != null) {
            http.secure(tlsCertificate, tlsCertificatePassword, null, null)
        }

        http.before { req, res ->
            // Assuming content-type is correctly set we will avoid logging binary request bodies
            if (!req.contentType().isNullOrBlank() && !req.contentType().contains(OCTET_CONTENT_TYPE) && !req.body().isNullOrBlank()) {
                logger.debug { "[${req.ip()}] ${req.requestMethod()} ${req.pathInfo()} with body: ${req.body()}" }
            } else {
                logger.debug { "[${req.ip()}] ${req.requestMethod()} ${req.pathInfo()}" }
            }

            res.header(ACCESS_CONTROL_ALLOW_ORIGIN, "*")
            res.header(ACCESS_CONTROL_REQUEST_METHOD, "POST, GET, OPTIONS")
            //res.header("Access-Control-Allow-Headers", "")
            res.type(JSON_CONTENT_TYPE)
        }

        http.after { _, res ->
            // This will not log binary response bodies
            if (!res.body().isNullOrBlank()) {
                logger.debug { "Response body: ${res.body()}" }
            }
        }

        http.path(basePath) {

            http.options("/*") { request, response ->
                request.headers(ACCESS_CONTROL_REQUEST_HEADERS)?.let {
                    response.header(ACCESS_CONTROL_ALLOW_HEADERS, it)
                }
                request.headers(ACCESS_CONTROL_REQUEST_METHOD)?.let {
                    response.header(ACCESS_CONTROL_ALLOW_METHODS, it)
                }
                "OK"
            }

            http.post("/tx/$PARAM_BLOCKCHAIN_RID", redirectPost { request, _ ->
                val tx = toTransaction(request)
                val maxLength = try {
                    if (tx.bytes.size > 200) 200 else tx.bytes.size
                } catch (e: Exception) {
                    throw UserMistake("Invalid tx format. Expected {\"tx\": <hex-string>}")
                }

                logger.debug {
                    """
                        Processed tx bytes: ${tx.bytes.sliceArray(0 until maxLength).toHex()}
                    """.trimIndent()
                }
                if (!tx.tx.matches(Regex("[0-9a-fA-F]{2,}"))) {
                    throw UserMistake("Invalid tx format. Expected {\"tx\": <hex-string>}")
                }
                model(request).postTransaction(tx)
                "{}"
            })

            http.get("/tx/$PARAM_BLOCKCHAIN_RID/$PARAM_HASH_HEX", JSON_CONTENT_TYPE, redirectGet { request, _ ->
                val result = runTxActionOnModel(request) { model, txRID ->
                    model.getTransaction(txRID)
                }
                gson.toJson(result)
            })

            http.get("/transactions/$PARAM_BLOCKCHAIN_RID/$PARAM_HASH_HEX", JSON_CONTENT_TYPE, redirectGet { request, _ ->
                val result = runTxActionOnModel(request) { model, txRID ->
                    model.getTransactionInfo(txRID)
                }
                gson.toJson(result)
            })

            http.get("/transactions/$PARAM_BLOCKCHAIN_RID", JSON_CONTENT_TYPE, redirectGet { request, _ ->
                val model = model(request)
                val paramsMap = request.queryMap()
                val limit = paramsMap.get("limit")?.value()?.toIntOrNull()?.coerceIn(0, MAX_NUMBER_OF_TXS_PER_REQUEST)
                        ?: DEFAULT_ENTRY_RESULTS_REQUEST
                val beforeTime = paramsMap.get("before-time")?.value()?.toLongOrNull() ?: Long.MAX_VALUE
                val result = model.getTransactionsInfo(beforeTime, limit)
                gson.toJson(result)
            })

            http.get("/tx/$PARAM_BLOCKCHAIN_RID/$PARAM_HASH_HEX/confirmationProof", JSON_CONTENT_TYPE, redirectGet { request, _ ->
                val result = runTxActionOnModel(request) { model, txRID ->
                    model.getConfirmationProof(txRID)
                }
                gson.toJson(result)
            })

            http.get("/tx/$PARAM_BLOCKCHAIN_RID/$PARAM_HASH_HEX/status", redirectGet { request, _ ->
                val result = runTxActionOnModel(request) { model, txRID ->
                    model.getStatus(txRID)
                }
                gson.toJson(result)
            })

            http.get("/blocks/$PARAM_BLOCKCHAIN_RID", JSON_CONTENT_TYPE, redirectGet { request, _ ->
                val model = model(request)
                val paramsMap = request.queryMap()
                val beforeTime = paramsMap.get("before-time")?.value()?.toLongOrNull()
                val beforeHeight = paramsMap.get("before-height")?.value()?.toLongOrNull()
                if (beforeTime != null && beforeHeight != null) {
                    throw UserMistake("Cannot specify both before-time and before-height")
                }
                val limit = paramsMap.get("limit")?.value()?.toIntOrNull()?.coerceIn(0, MAX_NUMBER_OF_BLOCKS_PER_REQUEST)
                        ?: DEFAULT_ENTRY_RESULTS_REQUEST
                val txHashesOnly = paramsMap.get("txs")?.value() != "true"
                val result = if (beforeHeight != null) {
                    model.getBlocksBeforeHeight(beforeHeight, limit, txHashesOnly)
                } else {
                    model.getBlocks(beforeTime ?: Long.MAX_VALUE, limit, txHashesOnly)
                }
                gson.toJson(result)
            })

            http.get("/blocks/$PARAM_BLOCKCHAIN_RID/$PARAM_HASH_HEX", JSON_CONTENT_TYPE, redirectGet { request, _ ->
                val model = model(request)
                val blockRID = request.params(PARAM_HASH_HEX).hexStringToByteArray()
                val txHashesOnly = request.queryMap()["txs"].value() != "true"
                val result = model.getBlock(blockRID, txHashesOnly)
                gson.toJson(result)
            })

            http.get("/blocks/$PARAM_BLOCKCHAIN_RID/$PARAM_HASH_HEX", OCTET_CONTENT_TYPE, redirectGet(OCTET_CONTENT_TYPE) { request, _ ->
                val model = model(request)
                val blockRID = request.params(PARAM_HASH_HEX).hexStringToByteArray()
                val txHashesOnly = request.queryMap()["txs"].value() != "true"
                val result = model.getBlock(blockRID, txHashesOnly)

                val gtv = result?.let { GtvObjectMapper.toGtvDictionary(it) } ?: GtvNull
                GtvEncoder.encodeGtv(gtv)
            })

            http.get("/blocks/$PARAM_BLOCKCHAIN_RID/height/$PARAM_HEIGHT", JSON_CONTENT_TYPE, redirectGet { request, _ ->
                val model = model(request)
                val height = request.params(PARAM_HEIGHT).toLong()
                val txHashesOnly = request.queryMap()["txs"].value() != "true"
                val result = model.getBlock(height, txHashesOnly)
                gson.toJson(result)
            })

            http.get("/blocks/$PARAM_BLOCKCHAIN_RID/height/$PARAM_HEIGHT", OCTET_CONTENT_TYPE, redirectGet(OCTET_CONTENT_TYPE) { request, _ ->
                val model = model(request)
                val height = request.params(PARAM_HEIGHT).toLong()
                val txHashesOnly = request.queryMap()["txs"].value() != "true"
                val result = model.getBlock(height, txHashesOnly)

                val gtv = result?.let { GtvObjectMapper.toGtvDictionary(it) } ?: GtvNull
                GtvEncoder.encodeGtv(gtv)
            })

            http.post("/query/$PARAM_BLOCKCHAIN_RID", redirectPost { request, _ ->
                handlePostQuery(request)
            })

            http.post("/batch_query/$PARAM_BLOCKCHAIN_RID", redirectPost { request, _ ->
                handleQueries(request)
            })

            // Direct query. That should be used as example: <img src="http://node/dquery/brid?type=get_picture&id=4555" />
            http.get("/dquery/$PARAM_BLOCKCHAIN_RID", redirectGet { request, response ->
                handleDirectQuery(request, response)
            })

            http.get("/query/$PARAM_BLOCKCHAIN_RID", redirectGet { request, _ ->
                handleGetQuery(request)
            })

            http.post("/query_gtx/$PARAM_BLOCKCHAIN_RID", redirectPost { request, _ ->
                handleGTXQueries(request)
            })

            http.post("/query_gtv/$PARAM_BLOCKCHAIN_RID", redirectPost(OCTET_CONTENT_TYPE) { request, _ ->
                handleGtvQuery(request)
            })

            http.get("/node/$PARAM_BLOCKCHAIN_RID/$SUBQUERY", JSON_CONTENT_TYPE, redirectGet { request, _ ->
                handleNodeStatusQueries(request)
            })

            http.get("/_debug", JSON_CONTENT_TYPE) { request, _ ->
                handleDebugQuery(request)
            }

            http.get("/_debug/$SUBQUERY", JSON_CONTENT_TYPE) { request, _ ->
                handleDebugQuery(request)
            }

            http.get("/brid/$PARAM_BLOCKCHAIN_RID") { request, response ->
                val brid = checkBlockchainRID(request)
                response.type("text/plain")
                brid
            }

            http.get("/blockchain/$PARAM_BLOCKCHAIN_RID/height", JSON_CONTENT_TYPE, redirectGet { request, _ ->
                val model = model(request)
                val result = model.getCurrentBlockHeight()
                gson.toJson(result)
            })

            http.get("/config/$PARAM_BLOCKCHAIN_RID", XML_CONTENT_TYPE, redirectGet(XML_CONTENT_TYPE) { request, _ ->
                val configuration = getBlockchainConfiguration(request)
                GtvMLEncoder.encodeXMLGtv(GtvDecoder.decodeGtv(configuration))
            })

            http.get("/config/$PARAM_BLOCKCHAIN_RID", OCTET_CONTENT_TYPE, redirectGet(OCTET_CONTENT_TYPE) { request, _ ->
                getBlockchainConfiguration(request)
            })

            http.post("/config/$PARAM_BLOCKCHAIN_RID", redirectPost { request, _ ->
                val configuration = try {
                    if (request.contentType().startsWith(OCTET_CONTENT_TYPE)) {
                        GtvDecoder.decodeGtv(request.bodyAsBytes())
                    } else {
                        GtvMLParser.parseGtvML(request.body())
                    }
                } catch (e: Exception) {
                    throw UserMistake("Cannot parse configuration: ${e.message ?: e.cause?.message}", e)
                }

                val model = model(request)
                try {
                    model.validateBlockchainConfiguration(configuration)
                } catch (e: UserMistake) {
                    throw e
                } catch (e: Exception) {
                    throw UserMistake("Invalid configuration: ${e.message}", e)
                }
                "{}"
            })
        }

        http.awaitInitialization()
    }

    private fun getBlockchainConfiguration(request: Request): ByteArray {
        val model = model(request)
        val paramsMap = request.queryMap()
        val height = paramsMap.get("height")?.value()?.toLongOrNull() ?: -1
        if (height < -1) throw BadFormatError("Height must be -1 (current height) or a non-negative integer")
        return model.getBlockchainConfiguration(height) ?: throw UserMistake("Failed to find configuration")
    }

    private fun toTransaction(request: Request): ApiTx {
        try {
            return gson.fromJson(request.body(), ApiTx::class.java)
        } catch (e: Exception) {
            throw UserMistake("Could not parse json", e)
        }
    }

    private fun toTxRID(hashHex: String): TxRID {
        val bytes: ByteArray
        try {
            bytes = hashHex.hexStringToByteArray()
        } catch (e: Exception) {
            throw UserMistake("Can't parse hashHex $hashHex", e)
        }

        val txRID: TxRID
        try {
            txRID = TxRID(bytes)
        } catch (e: Exception) {
            throw UserMistake("Bytes $hashHex is not a proper hash", e)
        }

        return txRID
    }

    private fun toJson(error: Exception): String {
        return gson.toJson(ErrorBody(error.message ?: "Unknown error"))
    }

    private fun handlePostQuery(request: Request): String {
        val model = model(request)
        val gtxQuery = gtvGson.fromJson(request.body(), Gtv::class.java)
        val queryDict = gtxQuery.asDict()
        val type = queryDict["type"] ?: throw UserMistake("Missing query type")
        val args = gtv(queryDict.filterKeys { key -> key != "type" })
        val queryResult = model.query(GtxQuery(type.asString(), args))
        return gtvToJSON(queryResult, gtvGson)
    }

    private fun handleGetQuery(request: Request): String {
        val model = model(request)
        val queryMap = request.queryMap()
        val type = queryMap.value("type") ?: throw UserMistake("Missing query type")
        val args = mutableMapOf<String, Gtv>()

        queryMap.toMap().filterKeys { it != "type" }.forEach {
            val paramValue = queryMap.value(it.key)
            if (paramValue == "true" || paramValue == "false") {
                args[it.key] = gtv(paramValue.toBoolean())
            } else if (paramValue.toLongOrNull() != null) {
                args[it.key] = gtv(paramValue.toLong())
            } else {
                args[it.key] = gtv(paramValue)
            }
        }

        val queryResult = model.query(GtxQuery(type, gtv(args)))
        return gtvToJSON(queryResult, gtvGson)
    }

    private fun handleDirectQuery(request: Request, response: Response): Any {
        val queryMap = request.queryMap()
        val args = GtvDictionary.build(queryMap.toMap().mapValues {
            gtv(queryMap.value(it.key))
        })
        val array = model(request).query(GtxQuery(queryMap.value("type"), args)).asArray()

        if (array.size < 2) {
            throw UserMistake("Response should have two parts: content-type and content")
        }
        // first element is content-type
        response.type(array[0].asString())
        val content = array[1]
        return when (content.type) {
            GtvType.STRING -> content.asString()
            GtvType.BYTEARRAY -> content.asByteArray()
            else -> throw UserMistake("Unexpected content")
        }
    }

    private fun handleQueries(request: Request): String {
        val model = model(request)
        val queriesArray: JsonArray = parseMultipleQueriesRequest(request)
        val response: MutableList<String> = mutableListOf()

        queriesArray.forEach {
            val gtxQuery = gtvGson.fromJson(it, Gtv::class.java)
            val queryDict = gtxQuery.asDict()
            val type = queryDict["type"] ?: throw UserMistake("Missing query type")
            val args = gtv(queryDict.filterKeys { key -> key != "type" })
            val queryResult = model.query(GtxQuery(type.asString(), args))
            response.add(gtvToJSON(queryResult, gtvGson))
        }

        return gson.toJson(response)
    }

    private fun handleGTXQueries(request: Request): String {
        val response: MutableList<String> = mutableListOf()
        val queriesArray: JsonArray = parseMultipleQueriesRequest(request)

        queriesArray.forEach {
            val hexQuery = it.asString
            val gtxQuery = try {
                GtxQuery.decode(hexQuery.hexStringToByteArray())
            } catch (e: IOException) {
                throw BadFormatError(e.message ?: "")
            }
            response.add(GtvEncoder.encodeGtv(model(request).query(gtxQuery)).toHex())
        }

        return gson.toJson(response)
    }

    private fun handleGtvQuery(request: Request): ByteArray {
        val gtvQuery = try {
            GtxQuery.decode(request.bodyAsBytes())
        } catch (e: IOException) {
            throw BadFormatError(e.message ?: "")
        }
        return GtvEncoder.encodeGtv(model(request).query(gtvQuery))
    }

    private fun handleNodeStatusQueries(request: Request): String {
        return model(request).nodeQuery(request.params(SUBQUERY))
    }

    private fun handleDebugQuery(request: Request): String {
        return models.values
                .filterIsInstance(Model::class.java)
                .firstOrNull()
                ?.debugQuery(request.params(SUBQUERY))
                ?: throw NotFoundError("There are no running chains")
    }

    private fun checkTxHashHex(request: Request): String {
        val hashHex = request.params(PARAM_HASH_HEX)
        if (!hashHex.matches(Regex("[0-9a-fA-F]{64}"))) {
            throw BadFormatError("Invalid hashHex. Expected 64 hex digits [0-9a-fA-F]")
        }
        return hashHex
    }

    /**
     * We allow two different syntax for finding the blockchain.
     * 1. provide BC RID
     * 2. provide Chain IID (should not be used in production, since to ChainIid could be anything).
     */
    private fun checkBlockchainRID(request: Request): String {
        val blockchainRID = request.params(PARAM_BLOCKCHAIN_RID)
        return when {
            blockchainRID.matches(Regex("[0-9a-fA-F]{64}")) -> blockchainRID
            blockchainRID.matches(Regex("iid_[0-9]*")) -> {
                val chainIid = blockchainRID.substring(4).toLong()
                val brid = bridByIID[chainIid]
                if (brid != null)
                    return brid
                else
                    throw NotFoundError("Can't find blockchain with chain Iid: $chainIid in DB. Did you add this BC to the node?")
            }

            else -> throw BadFormatError("Invalid blockchainRID. Expected 64 hex digits [0-9a-fA-F]")
        }
    }

    fun stop() {
        http.stop()
        http.awaitStop()
        System.gc()
        System.runFinalization()
    }

    private fun runTxActionOnModel(request: Request, txAction: (Model, TxRID) -> Any?): Any {
        val model = model(request)
        val txHashHex = checkTxHashHex(request)
        return txAction(model, toTxRID(txHashHex))
                ?: throw NotFoundError("Can't find tx with hash $txHashHex")
    }

    private fun chainModel(request: Request): ChainModel {
        val blockchainRID = checkBlockchainRID(request)
        val model = models[blockchainRID.uppercase()]
                ?: throw NotFoundError("Can't find blockchain with blockchainRID: $blockchainRID")

        if (!model.live) throw UnavailableException("Blockchain is unavailable")
        return model
    }

    private fun model(request: Request): Model {
        return chainModel(request) as Model
    }

    private fun parseMultipleQueriesRequest(request: Request): JsonArray {
        val element: JsonElement = gson.fromJson(request.body(), JsonElement::class.java)
        val jsonObject = element.asJsonObject
        return jsonObject.get("queries").asJsonArray
    }

    private fun redirectGet(responseType: String = JSON_CONTENT_TYPE, localHandler: (Request, Response) -> Any): (Request, Response) -> Any {
        return redirect(HttpMethod.GET, responseType, localHandler)
    }

    private fun redirectPost(responseType: String = JSON_CONTENT_TYPE, localHandler: (Request, Response) -> Any): (Request, Response) -> Any {
        return redirect(HttpMethod.POST, responseType, localHandler)
    }

    private fun redirect(method: HttpMethod, responseType: String = JSON_CONTENT_TYPE, localHandler: (Request, Response) -> Any): (Request, Response) -> Any {
        return { request, response ->
            response.type(responseType)
            val model = chainModel(request)
            if (model is ExternalModel) {
                logger.trace { "External REST API model found: $model" }
                when (method) {
                    HttpMethod.GET -> model.get(request, response)
                    HttpMethod.POST -> model.post(request, response)
                    else -> throw UnsupportedOperationException("Unsupported HTTP method: $method")
                }
            } else {
                logger.trace { "Local REST API model found: $model" }
                localHandler(request, response)
            }
        }
    }
}

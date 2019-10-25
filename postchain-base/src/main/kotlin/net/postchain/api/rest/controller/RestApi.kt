// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.api.rest.controller

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.request.receiveText
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.options
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import kotlinx.coroutines.launch
import mu.KLogging
import net.postchain.api.rest.controller.HttpHelper.Companion.ACCESS_CONTROL_ALLOW_HEADERS
import net.postchain.api.rest.controller.HttpHelper.Companion.ACCESS_CONTROL_ALLOW_METHODS
import net.postchain.api.rest.controller.HttpHelper.Companion.ACCESS_CONTROL_REQUEST_HEADERS
import net.postchain.api.rest.controller.HttpHelper.Companion.ACCESS_CONTROL_REQUEST_METHOD
import net.postchain.api.rest.controller.HttpHelper.Companion.PARAM_BLOCKCHAIN_RID
import net.postchain.api.rest.controller.HttpHelper.Companion.PARAM_HASH_HEX
import net.postchain.api.rest.controller.HttpHelper.Companion.PARAM_LIMIT
import net.postchain.api.rest.controller.HttpHelper.Companion.PARAM_UP_TO
import net.postchain.api.rest.controller.HttpHelper.Companion.SUBQUERY
import net.postchain.api.rest.json.JsonFactory
import net.postchain.api.rest.model.ApiTx
import net.postchain.api.rest.model.GTXQuery
import net.postchain.api.rest.model.TxRID
import net.postchain.common.TimeLog
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.core.UserMistake
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory

/**
 * Contains information on the rest API, such as network parameters and available queries
 */
class RestApi(
        private val basePath: String,
        private val httpServer: HttpServer
) : Modellable {

    companion object : KLogging()

    private val http = httpServer.http
    private val gson = JsonFactory.makeJson()
    private val models = mutableMapOf<String, Model>()

    init {
        buildRouter()
        logger.info { "Rest API listening on port ${actualPort()}" }
        logger.info { "Rest API attached on $basePath/" }
    }

    override fun attachModel(blockchainRID: String, model: Model) {
        models[blockchainRID.toUpperCase()] = model
    }

    override fun detachModel(blockchainRID: String) {
        models.remove(blockchainRID.toUpperCase())
    }

    override fun retrieveModel(blockchainRID: String): Model? {
        return models[blockchainRID.toUpperCase()]
    }

    fun actualPort(): Int {
        return 1
    }

    private fun buildRouter(){
        http.application.routing {  }.options(basePath + "/*") {
            call.request.headers[ACCESS_CONTROL_REQUEST_HEADERS]?.let {
                call.response.headers.append(ACCESS_CONTROL_ALLOW_HEADERS, it)
            }
            call.request.headers[ACCESS_CONTROL_REQUEST_METHOD]?.let {
                call.response.headers.append(ACCESS_CONTROL_ALLOW_METHODS, it)
            }
            call.respondText("OK")
        }
        http.application.routing {  }.post(basePath + "/tx/{$PARAM_BLOCKCHAIN_RID}") {
            val n = TimeLog.startSumConc("RestApi.buildRouter().postTx")
            val body = call.receiveText()
            logger.debug("Request body: ${body}")
            val tx = toTransaction(body)
            if (!tx.tx.matches(Regex("[0-9a-fA-F]{2,}"))) {
                throw UserMistake("Invalid tx format. Expected {\"tx\": <hexString>}")
            }
            model(call.parameters[PARAM_BLOCKCHAIN_RID] ?: "").postTransaction(tx)
            TimeLog.end("RestApi.buildRouter().postTx", n)
            call.respondText("{}")
        }
        http.application.routing {  }.get(basePath + "/tx/{$PARAM_BLOCKCHAIN_RID}/{$PARAM_HASH_HEX}") {
            runTxActionOnModel(call.parameters[PARAM_HASH_HEX] ?: "", call.parameters[PARAM_BLOCKCHAIN_RID] ?: "") { model, txRID ->
                launch {
                    call.respondText(gson.toJson(model.getTransaction(txRID)), io.ktor.http.ContentType.Application.Json)
                }
            }
        }
        http.application.routing {  }.get(basePath + "/tx/{$PARAM_BLOCKCHAIN_RID}/{$PARAM_HASH_HEX}/confirmationProof") {
            runTxActionOnModel(call.parameters[PARAM_HASH_HEX] ?: "", call.parameters[PARAM_BLOCKCHAIN_RID] ?: "") { model, txRID ->
                launch {
                    call.respondText(gson.toJson(model.getConfirmationProof(txRID)), io.ktor.http.ContentType.Application.Json)
                }

            }
        }
        http.application.routing {  }.get(basePath + "/tx/{$PARAM_BLOCKCHAIN_RID}/{$PARAM_HASH_HEX}/status") {
            runTxActionOnModel(call.parameters[PARAM_HASH_HEX] ?: "", call.parameters[PARAM_BLOCKCHAIN_RID] ?: "") { model, txRID ->
                launch {
                    call.respondText(gson.toJson(model.getStatus(txRID)), io.ktor.http.ContentType.Application.Json)
                }

            }
        }
        http.application.routing {  }.get(basePath + "/tx/{$PARAM_BLOCKCHAIN_RID}/blocks/latest/{$PARAM_UP_TO}/limit/{$PARAM_LIMIT}") {
            val model = model(call.parameters[PARAM_BLOCKCHAIN_RID] ?: "")
            try {
                val upTo = call.parameters[PARAM_UP_TO]!!.toLong()
                val limit = call.parameters[PARAM_LIMIT]!!.toInt()
                call.respondText(gson.toJson(model.getLatestBlocksUpTo(upTo, limit)), io.ktor.http.ContentType.Application.Json)
            } catch (e: NumberFormatException) {
                throw BadFormatError("Format is not correct (Long, Int)")
            }
        }
        http.application.routing {  }.post(basePath + "/query/{$PARAM_BLOCKCHAIN_RID}") {
            call.respondText(handleQuery(call.parameters[PARAM_BLOCKCHAIN_RID] ?: "", call.receiveText()))
        }
        http.application.routing {  }.post(basePath + "/batch_query/{$PARAM_BLOCKCHAIN_RID}") {
            call.respondText(handleQueries(call.parameters[PARAM_BLOCKCHAIN_RID] ?: "", call.receiveText()))
        }
        http.application.routing {  }.post(basePath + "/query_gtx/{$PARAM_BLOCKCHAIN_RID}") {
            call.respondText(handleGTXQueries(call.parameters[PARAM_BLOCKCHAIN_RID] ?: "", call.receiveText()))
        }
        http.application.routing {  }.post(basePath + "/node/{$PARAM_BLOCKCHAIN_RID}/{$SUBQUERY}") {
            call.respondText(handleNodeStatusQueries(call.parameters[PARAM_BLOCKCHAIN_RID] ?: "", call.parameters[SUBQUERY] ?: "", call.receiveText()))
        }
    }

    private fun toTransaction(requestBody: String): ApiTx {
        try {
            return gson.fromJson<ApiTx>(requestBody , ApiTx::class.java)
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

    private fun toGTXQuery(json: String): GTXQuery {
        try {
            val gson = Gson()
            return gson.fromJson<GTXQuery>(json, GTXQuery::class.java)
        } catch (e: Exception) {
            throw UserMistake("Could not parse json", e)
        }
    }

    private fun handleQuery(paramBlockchainRID: String, requestBody: String): String {
        logger.debug("Request body: ${requestBody}")
        return model(paramBlockchainRID)
                .query(Query(requestBody))
                .json
    }

    private fun handleQueries(paramBlockchainRID: String, requestBody: String): String {
        logger.debug("Request body: ${requestBody}")

        val queriesArray: JsonArray = parseMultipleQueriesRequest(requestBody)

        var response: MutableList<String> = mutableListOf()

        queriesArray.forEach {
            var query = gson.toJson(it)
            response.add(model(paramBlockchainRID).query(Query(query)).json)
        }

        return gson.toJson(response)
    }

    private fun handleGTXQueries(paramBlockchainRID: String, requestBody: String): String {
        logger.debug("Request body: ${requestBody}")
        var response: MutableList<String> = mutableListOf<String>()
        val queriesArray: JsonArray = parseMultipleQueriesRequest(requestBody)

        queriesArray.forEach {
            val hexQuery = it.asString
            val gtxQuery = GtvFactory.decodeGtv(hexQuery.hexStringToByteArray())
            response.add(GtvEncoder.encodeGtv(model(paramBlockchainRID).query(gtxQuery)).toHex())
        }

        return gson.toJson(response)
    }

    private fun handleNodeStatusQueries(paramBlockchainRID: String, paramSubquery: String,  requestBody: String): String {
        logger.debug("Request body: ${requestBody}")
        return model(paramBlockchainRID).nodeQuery(paramSubquery)
    }

    private fun checkTxHashHex(hashHex: String): String {
        if (!hashHex.matches(Regex("[0-9a-fA-F]{64}"))) {
            throw BadFormatError("Invalid hashHex. Expected 64 hex digits [0-9a-fA-F]")
        }
        return hashHex
    }

    private fun checkBlockchainRID(paramBlockchainRID: String): String {
        if (!paramBlockchainRID.matches(Regex("[0-9a-fA-F]{64}"))) {
            throw BadFormatError("Invalid blockchainRID. Expected 64 hex digits [0-9a-fA-F]")
        }
        return paramBlockchainRID
    }

    fun stop() {
        //val routes = getAccssibleRoutes(http)
       // routes.remove("$basePath/query/$PARAM_BLOCKCHAIN_RID", "post")

        //TODO
        // Need to remove the rest of routes as well once spark support to access Routes object.
    }

//    private fun getAccssibleRoutes(http: Service): Routes {
//        val clazz = http::class.java
//        val routesField= clazz.getDeclaredField("routes")
//        routesField.isAccessible = true
//        return routesField.get(http) as Routes
//    }
//
    private fun runTxActionOnModel(paramHashHex: String, paramBlockchainRID: String, txAction: (Model, TxRID) -> Any?): Any? {
        val model = model(paramBlockchainRID)
        val txHashHex = checkTxHashHex(paramHashHex)
        return txAction(model, toTxRID(txHashHex))
                ?: throw NotFoundError("Can't find tx with hash $txHashHex")
    }

    private fun model(paramBlockchainRID: String): Model {
        val blockchainRID = checkBlockchainRID(paramBlockchainRID)
        return models[blockchainRID.toUpperCase()]
                ?: throw NotFoundError("Can't find blockchain with blockchainRID: $blockchainRID")
    }

    private fun parseMultipleQueriesRequest(requestBody: String): JsonArray {
        val element: JsonElement = gson.fromJson(requestBody, JsonElement::class.java)
        val jsonObject = element.asJsonObject
        return jsonObject.get("queries").asJsonArray
    }

}

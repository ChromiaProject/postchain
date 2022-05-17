// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.client.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.tx.TransactionStatus.*
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.GTXDataBuilder
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.io.entity.StringEntity
import java.io.BufferedReader
import java.io.InputStream

private const val APPLICATION_JSON = "application/json"

class ConcretePostchainClient(
        private val resolver: PostchainNodeResolver,
        private val blockchainRID: BlockchainRid,
        private val defaultSigner: DefaultSigner?
) : PostchainClient {

    companion object : KLogging()

    private val gson = GsonBuilder().create()!! // We don't use any adapters b/c this is very simple
    private val serverUrl = resolver.getNodeURL(blockchainRID)
    private val httpClient = HttpClients.createDefault()
    private val blockchainRIDHex = blockchainRID.toHex()
    private val retrieveTxStatusAttempts = 20
    private val retrieveTxStatusIntervalMs = 500L

    override fun makeTransaction(): GTXTransactionBuilder {
        return GTXTransactionBuilder(this, blockchainRID, arrayOf(defaultSigner!!.pubkey))
    }

    override fun makeTransaction(signers: Array<ByteArray>): GTXTransactionBuilder {
        return GTXTransactionBuilder(this, blockchainRID, signers)
    }

    override fun postTransaction(txBuilder: GTXDataBuilder, confirmationLevel: ConfirmationLevel): Promise<TransactionResult, Exception> {
        return task { doPostTransaction(txBuilder, confirmationLevel) }
    }

    override fun postTransactionSync(txBuilder: GTXDataBuilder, confirmationLevel: ConfirmationLevel): TransactionResult {
        return doPostTransaction(txBuilder, confirmationLevel)
    }

    override fun query(name: String, gtv: Gtv): Promise<Gtv, Exception> {
        return task { doQuery(name, gtv) }
    }

    override fun querySync(name: String, gtv: Gtv) = doQuery(name, gtv)

    private fun doQuery(name: String, gtv: Gtv): Gtv {
        val httpPost = HttpPost("$serverUrl/query_gtx/$blockchainRIDHex")
        val gtxQuery = gtv(gtv(name), gtv)
        val jsonQuery = """{"queries" : ["${GtvEncoder.encodeGtv(gtxQuery).toHex()}"]}""".trimMargin()
        with(httpPost) {
            entity = StringEntity(jsonQuery)
            setHeader("Accept", APPLICATION_JSON)
            setHeader("Content-type", APPLICATION_JSON)
        }
        httpClient.execute(httpPost).use { response ->
            val contentType: String = response.entity.contentType
            val responseBody = parseResponse(response.entity.content)
            if (response.code != 200) {
                val errorMessage = if (contentType.equals(APPLICATION_JSON, ignoreCase = true)) {
                    val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)
                    jsonObject.get("error")?.asString
                } else {
                    null
                }
                throw UserMistake(errorMessage ?: "Can not make query_gtx api call: ${response.code} ${response.reasonPhrase}")
            }
            val type = object : TypeToken<List<String>>() {}.type
            val gtxHexCode = gson.fromJson<List<String>>(responseBody, type)?.first()
            return GtvFactory.decodeGtv(gtxHexCode!!.hexStringToByteArray())
        }
    }

    private fun doPostTransaction(txBuilder: GTXDataBuilder, confirmationLevel: ConfirmationLevel): TransactionResult {
        val txHex = txBuilder.serialize().toHex()
        val txJson = """{"tx" : $txHex}"""
        val txHashHex = txBuilder.getDigestForSigning().toHex()

        fun submitTransaction(): Int {
            val httpPost = HttpPost("$serverUrl/tx/$blockchainRIDHex")
            httpPost.setHeader("Content-type", APPLICATION_JSON)
            httpPost.entity = StringEntity(txJson)
            return httpClient.execute(httpPost).use { response -> response.code }
        }

        when (confirmationLevel) {

            ConfirmationLevel.NO_WAIT -> {
                val statusCode = submitTransaction()
                return if (statusCode == 200) {
                    TransactionResultImpl(WAITING)
                } else {
                    TransactionResultImpl(REJECTED)
                }
            }

            ConfirmationLevel.UNVERIFIED -> {
                val statusCode = submitTransaction()
                if (statusCode in 400..499) {
                    return TransactionResultImpl(REJECTED)
                }
                val httpGet = HttpGet("$serverUrl/tx/$blockchainRIDHex/$txHashHex/status")
                httpGet.setHeader("Content-type", APPLICATION_JSON)

                // keep polling till getting Confirmed or Rejected
                (0 until retrieveTxStatusAttempts).forEach { _ ->
                    try {
                        httpClient.execute(httpGet).use { response ->
                            response.entity?.let {
                                val responseBody = parseResponse(it.content)
                                val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)
                                val statusString = jsonObject.get("status")?.asString?.toUpperCase()
                                if (statusString == null) {
                                    logger.warn { "No status in response\n$responseBody" }
                                } else {
                                    val status = valueOf(statusString)

                                    if (status == CONFIRMED || status == REJECTED) {
                                        return TransactionResultImpl(status)
                                    }
                                }

                                Thread.sleep(retrieveTxStatusIntervalMs)
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn(e) { "Unable to poll for new block" }
                        Thread.sleep(retrieveTxStatusIntervalMs)
                    }
                }

                return TransactionResultImpl(REJECTED)
            }

            else -> {
                return TransactionResultImpl(REJECTED)
            }
        }
    }

    private fun parseResponse(content: InputStream): String {
        return content.bufferedReader().use(BufferedReader::readText)
    }

}
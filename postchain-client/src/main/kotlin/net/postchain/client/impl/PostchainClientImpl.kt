// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.client.impl

import com.google.gson.Gson
import com.google.gson.JsonParseException
import mu.KLogging
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.BlockDetail
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.TransactionResult
import net.postchain.client.core.TxRid
import net.postchain.client.request.Endpoint
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.common.tx.TransactionStatus
import net.postchain.common.tx.TransactionStatus.*
import net.postchain.crypto.KeyPair
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtx.Gtx
import org.apache.commons.io.input.BoundedInputStream
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.cookie.StandardCookieSpec
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.util.Timeout
import org.http4k.client.ApacheClient
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.lang.Thread.sleep
import java.time.Duration

/* JSON structures */
data class Tx(val tx: String)
data class TxStatus(val status: String?, val rejectReason: String?)
data class CurrentBlockHeight(val blockHeight: Long)
data class ErrorResponse(val error: String)

class PostchainClientImpl(
        override val config: PostchainClientConfig,
        private val httpClient: HttpHandler = ApacheClient(HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setRedirectsEnabled(false)
                        .setCookieSpec(StandardCookieSpec.IGNORE)
                        .setConnectionRequestTimeout(Timeout.ofMilliseconds(config.connectTimeout.toMillis()))
                        .setResponseTimeout(Timeout.ofMilliseconds(config.responseTimeout.toMillis()))
                        .build()).build()),
) : PostchainClient {

    companion object : KLogging()

    private fun nextEndpoint() = config.endpointPool.next()
    private val blockchainRIDHex = config.blockchainRid.toHex()
    private val blockchainRIDOrID = config.queryByChainId?.let { "iid_$it" } ?: blockchainRIDHex
    private val cryptoSystem = config.cryptoSystem
    private val calculator = GtvMerkleHashCalculator(cryptoSystem)
    private val gson = Gson()

    override fun transactionBuilder() = transactionBuilder(config.signers)

    override fun transactionBuilder(signers: List<KeyPair>) = TransactionBuilder(
            this,
            config.blockchainRid,
            signers.map { it.pubKey.data },
            signers.map { it.sigMaker(cryptoSystem) },
            cryptoSystem
    )

    @Throws(IOException::class)
    override fun query(name: String, gtv: Gtv): Gtv {
        var queryResult: Response? = null
        var responseStream: BoundedInputStream? = null
        for (j in 1..config.endpointPool.size()) {
            val endpoint = nextEndpoint()
            val request = createQueryRequest(name, gtv, endpoint)
            endpoint@ for (i in 1..config.failOverConfig.attemptsPerEndpoint) {
                queryResult = queryTo(request, endpoint)
                responseStream = BoundedInputStream(queryResult.body.stream, config.maxResponseSize.toLong())
                when (queryResult.status) {
                    Status.OK -> return GtvDecoder.decodeGtv(responseStream)
                    Status.BAD_REQUEST -> throw buildExceptionFromGTV(responseStream, queryResult.status)
                    Status.INTERNAL_SERVER_ERROR -> throw buildExceptionFromGTV(responseStream, queryResult.status)
                    Status.NOT_FOUND -> throw buildExceptionFromGTV(responseStream, queryResult.status)
                    Status.UNKNOWN_HOST -> break@endpoint
                    Status.SERVICE_UNAVAILABLE -> break@endpoint
                }
                sleep(config.failOverConfig.attemptInterval.toMillis())
            }
        }
        throw buildExceptionFromGTV(responseStream!!, queryResult!!.status)
    }

    private fun createQueryRequest(
            name: String,
            gtv: Gtv,
            endpoint: Endpoint,
    ): Request {
        val gtxQuery = gtv(gtv(name), gtv)
        val encodedQuery = GtvEncoder.encodeGtv(gtxQuery)
        return Request(Method.POST, "${endpoint.url}/query_gtv/$blockchainRIDOrID")
                .body(encodedQuery.inputStream())
    }

    private fun queryTo(request: Request, endpoint: Endpoint): Response {
        val response = httpClient(request)
        if (response.status == Status.SERVICE_UNAVAILABLE) endpoint.setUnreachable()
        return response
    }

    private fun buildExceptionFromGTV(responseStream: InputStream, status: Status): UserMistake {
        val errorMessage = GtvDecoder.decodeGtv(responseStream).asString()
        return UserMistake("Can not make a query: $status $errorMessage")
    }

    @Throws(IOException::class)
    override fun currentBlockHeight(): Long {
        val endpoint = nextEndpoint()
        val request = Request(Method.GET, "${endpoint.url}/node/$blockchainRIDOrID/height")
        val response = queryTo(request, endpoint)
        val body = BoundedInputStream(response.body.stream, 1024).bufferedReader()
        if (response.status != Status.OK) {
            val msg = parseJson(body, ErrorResponse::class.java)?.error ?: "Unknown error"
            throw UserMistake("Can not make a query: ${response.status} $msg")
        }
        return parseJson(body, CurrentBlockHeight::class.java)?.blockHeight ?: throw IOException("Json parsing failed")
    }

    @Throws(IOException::class)
    override fun blockAtHeight(height: Long): BlockDetail? {
        val endpoint = nextEndpoint()
        val request = Request(Method.GET, "${endpoint.url}/blocks/$blockchainRIDOrID/height/$height")
                .header("Accept", ContentType.OCTET_STREAM.value)
        val response = queryTo(request, endpoint)
        val responseStream = BoundedInputStream(response.body.stream, config.maxResponseSize.toLong())
        if (response.status != Status.OK) {
            throw buildExceptionFromGTV(responseStream, response.status)
        }

        val gtv = GtvDecoder.decodeGtv(responseStream)
        return if (gtv.isNull()) null else GtvObjectMapper.fromGtv(gtv, BlockDetail::class)
    }

    @Throws(IOException::class)
    override fun postTransaction(tx: Gtx): TransactionResult {
        var result: TransactionResult? = null
        for (j in 1..config.endpointPool.size()) {
            val endpoint = nextEndpoint()
            endpoint@ for (i in 1..config.failOverConfig.attemptsPerEndpoint) {
                result = postTransactionTo(tx, endpoint)
                when (result.httpStatusCode) {
                    Status.OK.code, Status.BAD_REQUEST.code, Status.NOT_FOUND.code, Status.CONFLICT.code -> return result
                    Status.SERVICE_UNAVAILABLE.code -> break@endpoint
                }
                sleep(config.failOverConfig.attemptInterval.toMillis())
            }
        }
        return result!!
    }

    private fun postTransactionTo(tx: Gtx, endpoint: Endpoint): TransactionResult {
        val txRid = TxRid(tx.calculateTxRid(calculator).toHex())
        val request = Request(Method.POST, "${endpoint.url}/tx/$blockchainRIDHex")
                .body(gson.toJson(Tx(tx.encodeHex())))
        val response = httpClient(request)
        if (response.status == Status.SERVICE_UNAVAILABLE) endpoint.setUnreachable()
        val status = if (response.status == Status.OK) WAITING else REJECTED
        return TransactionResult(txRid, status, response.status.code, response.status.description)
    }

    @Throws(IOException::class)
    override fun postTransactionAwaitConfirmation(tx: Gtx): TransactionResult {
        val result = postTransaction(tx)
        if (result.status == REJECTED) {
            return result
        }
        return awaitConfirmation(result.txRid, config.statusPollCount, config.statusPollInterval)
    }

    @Throws(IOException::class)
    override fun awaitConfirmation(txRid: TxRid, retries: Int, pollInterval: Duration): TransactionResult {
        var lastKnownTxResult = TransactionResult(txRid, UNKNOWN, null, null)
        // keep polling till getting Confirmed or Rejected
        run poll@{
            repeat(retries) {
                try {
                    lastKnownTxResult = checkTxStatus(txRid)
                    if (lastKnownTxResult.status == CONFIRMED || lastKnownTxResult.status == REJECTED) return@poll
                } catch (e: Exception) {
                    logger.warn(e) { "Unable to poll for new block" }
                    lastKnownTxResult = TransactionResult(txRid, UNKNOWN, null, null)
                }
                sleep(pollInterval.toMillis())
            }
        }
        return lastKnownTxResult
    }

    override fun checkTxStatus(txRid: TxRid): TransactionResult {
        val endpoint = nextEndpoint()
        val validationRequest = Request(Method.GET, "${endpoint.url}/tx/$blockchainRIDOrID/${txRid.rid}/status")
        val response = queryTo(validationRequest, endpoint)
        val responseStream = BoundedInputStream(response.body.stream, 64 * 1024)
        val txStatus = parseJson(responseStream.bufferedReader(), TxStatus::class.java)
        return TransactionResult(
                txRid,
                TransactionStatus.valueOf(txStatus?.status?.uppercase() ?: "UNKNOWN"),
                response.status.code,
                txStatus?.rejectReason
        )
    }

    private fun <T> parseJson(body: BufferedReader, cls: Class<T>): T? = try {
        gson.fromJson(body, cls)
    } catch (e: JsonParseException) {
        val rootCause = ExceptionUtils.getRootCause(e)
        if (rootCause is IOException) throw rootCause
        else throw IOException("Json parsing failed", e)
    }
}

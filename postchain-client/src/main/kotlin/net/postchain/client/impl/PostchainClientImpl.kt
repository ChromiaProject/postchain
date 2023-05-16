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
import net.postchain.client.defaultHttpHandler
import net.postchain.client.exception.ClientError
import net.postchain.client.request.Endpoint
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.tx.TransactionStatus
import net.postchain.common.tx.TransactionStatus.*
import net.postchain.crypto.KeyPair
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtx.Gtx
import net.postchain.gtx.GtxQuery
import org.apache.commons.io.input.BoundedInputStream
import org.apache.commons.lang3.exception.ExceptionUtils
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import java.io.EOFException
import java.io.IOException
import java.lang.Thread.sleep
import java.time.Duration

object Header {
    const val ContentType = "Content-Type"
    const val Accept = "Accept"
}

class PostchainClientImpl(
        override val config: PostchainClientConfig,
        httpClient: HttpHandler = defaultHttpHandler(config),
) : PostchainClient {

    companion object : KLogging()

    private val blockchainRIDHex = config.blockchainRid.toHex()
    private val blockchainRIDOrID = config.queryByChainId?.let { "iid_$it" } ?: blockchainRIDHex
    private val cryptoSystem = config.cryptoSystem
    private val calculator = GtvMerkleHashCalculator(cryptoSystem)
    private val gson = Gson()
    private val requestStrategy = config.requestStrategy.create(config, httpClient)

    override fun transactionBuilder() = transactionBuilder(config.signers)

    override fun transactionBuilder(signers: List<KeyPair>) = TransactionBuilder(
            this,
            config.blockchainRid,
            signers.map { it.pubKey.data },
            signers.map { it.sigMaker(cryptoSystem) },
            cryptoSystem
    )

    @Throws(IOException::class)
    override fun query(name: String, args: Gtv): Gtv = requestStrategy.request({ endpoint ->
        val gtxQuery = GtxQuery(name, args)
        Request(Method.POST, "${endpoint.url}/query_gtv/$blockchainRIDOrID")
                .header(Header.ContentType, ContentType.OCTET_STREAM.value)
                .header(Header.Accept, ContentType.OCTET_STREAM.value)
                .body(gtxQuery.encode().inputStream())
    }, { response, endpoint ->
        decodeGtv("query", response, endpoint)
    }, { response, endpoint ->
        buildExceptionFromErrorResponse("query", response, endpoint)
    },
            true)

    @Throws(IOException::class)
    override fun currentBlockHeight(): Long = requestStrategy.request({ endpoint ->
        Request(Method.GET, "${endpoint.url}/blockchain/$blockchainRIDOrID/height")
                .header(Header.Accept, ContentType.APPLICATION_JSON.value)
    }, { response, endpoint ->
        parseJson("currentBlockHeight", response, endpoint, CurrentBlockHeight::class.java).blockHeight
    }, { response, endpoint ->
        buildExceptionFromErrorResponse("currentBlockHeight", response, endpoint)
    },
            false)

    @Throws(IOException::class)
    override fun blockAtHeight(height: Long): BlockDetail? = requestStrategy.request({ endpoint ->
        Request(Method.GET, "${endpoint.url}/blocks/$blockchainRIDOrID/height/$height")
                .header(Header.Accept, ContentType.OCTET_STREAM.value)
    }, { response, endpoint ->
        val gtv = decodeGtv("blockAtHeight", response, endpoint)
        if (gtv.isNull()) null else GtvObjectMapper.fromGtv(gtv, BlockDetail::class)
    }, { response, endpoint ->
        buildExceptionFromErrorResponse("blockAtHeight", response, endpoint)
    },
            true)

    @Throws(IOException::class)
    override fun postTransaction(tx: Gtx): TransactionResult {
        val txRid = TxRid(tx.calculateTxRid(calculator).toHex())
        return requestStrategy.request({ endpoint ->
            Request(Method.POST, "${endpoint.url}/tx/$blockchainRIDHex")
                    .header(Header.ContentType, ContentType.APPLICATION_JSON.value)
                    .header(Header.Accept, ContentType.APPLICATION_JSON.value)
                    .body(gson.toJson(Tx(tx.encodeHex())))
        }, { response, _ ->
            TransactionResult(txRid, WAITING, response.status.code, response.status.description)
        }, { response, _ ->
            val rejectReason = parseErrorResponse(response)
            TransactionResult(txRid, REJECTED, response.status.code, rejectReason)
        }, false)
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

    @Throws(IOException::class)
    override fun checkTxStatus(txRid: TxRid): TransactionResult = requestStrategy.request({ endpoint ->
        Request(Method.GET, "${endpoint.url}/tx/$blockchainRIDOrID/${txRid.rid}/status")
                .header(Header.Accept, ContentType.APPLICATION_JSON.value)
    }, { response, endpoint ->
        val txStatus = parseJson("checkTxStatus", response, endpoint, TxStatus::class.java)
        TransactionResult(
                txRid,
                TransactionStatus.valueOf(txStatus.status?.uppercase() ?: "UNKNOWN"),
                response.status.code,
                txStatus.rejectReason
        )
    }, { response, endpoint ->
        buildExceptionFromErrorResponse("checkTxStatus", response, endpoint)
    },
            true)

    @Throws(IOException::class)
    override fun confirmationProof(txRid: TxRid): ByteArray = requestStrategy.request({ endpoint ->
        Request(Method.GET, "${endpoint.url}/tx/$blockchainRIDOrID/${txRid.rid}/confirmationProof")
                .header(Header.Accept, ContentType.APPLICATION_JSON.value)
    }, { response, endpoint ->
        val confirmationProof = parseJson("confirmationProof", response, endpoint, ConfirmationProof::class.java)
        confirmationProof.proof.hexStringToByteArray()
    }, { response, endpoint ->
        buildExceptionFromErrorResponse("confirmationProof", response, endpoint)
    },
            true)

    @Throws(IOException::class)
    override fun getTransaction(txRid: TxRid): ByteArray = requestStrategy.request({ endpoint ->
        Request(Method.GET, "${endpoint.url}/tx/$blockchainRIDOrID/${txRid.rid}")
                .header(Header.Accept, ContentType.OCTET_STREAM.value)
    }, { response, endpoint ->
        when (val responseType = response.header(Header.ContentType)) {
            ContentType.OCTET_STREAM.value -> responseStream(response).use { it.readAllBytes() }

            ContentType.APPLICATION_JSON.value -> {
                val txResponse = parseJson("getTransaction", response, endpoint, Transaction::class.java)
                txResponse.tx.hexStringToByteArray()
            }

            else -> throw ClientError("getTransaction", response.status,
                    "Unexpected response type: $responseType", endpoint)
        }
    }, { response, endpoint ->
        buildExceptionFromErrorResponse("getTransaction", response, endpoint)
    },
            true)

    private fun <T> parseJson(context: String, response: Response, endpoint: Endpoint, cls: Class<T>): T =
            parseJson(responseStream(response), cls)
                    ?: throw ClientError(context, response.status, "JSON parsing failed", endpoint)

    private fun decodeGtv(context: String, response: Response, endpoint: Endpoint) =
            decodeGtv(responseStream(response))
                    ?: throw ClientError(context, response.status, "GTV decoding failed", endpoint)

    private fun buildExceptionFromErrorResponse(context: String, response: Response, endpoint: Endpoint): Nothing {
        val errorMessage = parseErrorResponse(response)
        throw ClientError(context, response.status, errorMessage, endpoint)
    }

    private fun parseErrorResponse(response: Response): String {
        val responseStream = responseStream(response)
        return when (response.header(Header.ContentType)) {
            ContentType.APPLICATION_JSON.value -> parseJson(responseStream, ErrorResponse::class.java)?.error
                    ?: response.status.description

            ContentType.OCTET_STREAM.value ->
                decodeGtv(responseStream)?.asString() ?: response.status.description

            else -> {
                val responseBody = responseStream(response).use { it.readAllBytes() }
                if (responseBody.isNotEmpty()) String(responseBody) else response.status.description
            }
        }
    }

    private fun <T> parseJson(responseStream: BoundedInputStream, cls: Class<T>): T? = try {
        val body = responseStream.bufferedReader()
        gson.fromJson(body, cls)
    } catch (e: JsonParseException) {
        val rootCause = ExceptionUtils.getRootCause(e)
        if (rootCause is IOException) throw rootCause
        else null
    }

    private fun decodeGtv(responseStream: BoundedInputStream): Gtv? = try {
        GtvDecoder.decodeGtv(responseStream)
    } catch (e: EOFException) {
        throw e
    } catch (e: IOException) {
        val rootCause = ExceptionUtils.getRootCause(e)
        if (rootCause is IOException) throw rootCause
        else null
    }

    private fun responseStream(response: Response) =
            BoundedInputStream(response.body.stream, config.maxResponseSize.toLong())

    @Throws(IOException::class)
    override fun close() {
        requestStrategy.close()
    }

    /* JSON structures */
    data class Tx(val tx: String)
    data class TxStatus(val status: String?, val rejectReason: String?)
    data class CurrentBlockHeight(val blockHeight: Long)
    data class ErrorResponse(val error: String)
}

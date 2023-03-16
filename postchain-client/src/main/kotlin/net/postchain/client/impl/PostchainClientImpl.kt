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
import java.io.IOException
import java.lang.Thread.sleep
import java.time.Duration

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
                .header("Content-Type", ContentType.OCTET_STREAM.value)
                .header("Accept", ContentType.OCTET_STREAM.value)
                .body(gtxQuery.encode().inputStream())
    }, ::decodeGtv, ::buildExceptionFromGTV, true)

    @Throws(IOException::class)
    override fun currentBlockHeight(): Long = requestStrategy.request({ endpoint ->
        Request(Method.GET, "${endpoint.url}/node/$blockchainRIDOrID/height")
                .header("Accept", ContentType.APPLICATION_JSON.value)
    }, { response ->
        parseJson(response, CurrentBlockHeight::class.java)?.blockHeight
                ?: throw IOException("Json parsing failed")
    }, { response ->
        val msg = parseJson(response, ErrorResponse::class.java)?.error ?: "Unknown error"
        throw ClientError("Cannot fetch current block height: ${response.status} $msg")
    }, false)

    @Throws(IOException::class)
    override fun blockAtHeight(height: Long): BlockDetail? = requestStrategy.request({ endpoint ->
        Request(Method.GET, "${endpoint.url}/blocks/$blockchainRIDOrID/height/$height")
                .header("Accept", ContentType.OCTET_STREAM.value)
    }, { response ->
        val gtv = decodeGtv(response)
        if (gtv.isNull()) null else GtvObjectMapper.fromGtv(gtv, BlockDetail::class)
    }, ::buildExceptionFromGTV, true)

    private fun decodeGtv(response: Response) =
            GtvDecoder.decodeGtv(BoundedInputStream(response.body.stream, config.maxResponseSize.toLong()))

    private fun buildExceptionFromGTV(response: Response): Nothing {
        val responseStream = BoundedInputStream(response.body.stream, config.maxResponseSize.toLong())
        val errorMessage = try {
            GtvDecoder.decodeGtv(responseStream).asString()
        } catch (e: IOException) {
            // Error body can't be parsed as GTV, this could be a client generated error
            // Dump it as a string and hope it is either empty or readable text
            String(responseStream.readAllBytes())
        }
        throw ClientError("Can not make a query: ${response.status} $errorMessage")
    }

    @Throws(IOException::class)
    override fun postTransaction(tx: Gtx): TransactionResult {
        val txRid = TxRid(tx.calculateTxRid(calculator).toHex())
        return requestStrategy.request({ endpoint ->
            Request(Method.POST, "${endpoint.url}/tx/$blockchainRIDHex")
                    .header("Content-Type", ContentType.APPLICATION_JSON.value)
                    .header("Accept", ContentType.APPLICATION_JSON.value)
                    .body(gson.toJson(Tx(tx.encodeHex())))
        }, { response ->
            TransactionResult(txRid, WAITING, response.status.code, response.status.description)
        }, { response ->
            val rejectReason =
                    parseJson(response, ErrorResponse::class.java)?.error
                            ?: response.status.description
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

    override fun checkTxStatus(txRid: TxRid): TransactionResult = requestStrategy.request({ endpoint ->
        Request(Method.GET, "${endpoint.url}/tx/$blockchainRIDOrID/${txRid.rid}/status")
                .header("Accept", ContentType.APPLICATION_JSON.value)
    }, { response ->
        val txStatus = parseJson(response, TxStatus::class.java)
        TransactionResult(
                txRid,
                TransactionStatus.valueOf(txStatus?.status?.uppercase() ?: "UNKNOWN"),
                response.status.code,
                txStatus?.rejectReason
        )
    }, { response ->
        val msg = parseJson(response, ErrorResponse::class.java)?.error ?: "Unknown error"
        throw ClientError("Can not check transaction status: ${response.status} $msg")
    }, true)

    override fun confirmationProof(txRid: TxRid): ByteArray? = requestStrategy.request({ endpoint ->
        Request(Method.GET, "${endpoint.url}/tx/$blockchainRIDOrID/${txRid.rid}/confirmationProof")
                .header("Accept", ContentType.APPLICATION_JSON.value)
    }, { response ->
        val confirmationProof = parseJson(response, ConfirmationProof::class.java)
        confirmationProof?.proof?.hexStringToByteArray()
    }, { response ->
        val msg = parseJson(response, ErrorResponse::class.java)?.error ?: "Unknown error"
        throw ClientError("Can not fetch confirmation proof: ${response.status} $msg")
    }, true)

    override fun getTransaction(txRid: TxRid): ByteArray? = requestStrategy.request({ endpoint ->
        Request(Method.GET, "${endpoint.url}/tx/$blockchainRIDOrID/${txRid.rid}")
                .header("Accept", ContentType.APPLICATION_JSON.value)
    }, { response ->
        val txResponse = parseJson(response, Transaction::class.java)
        txResponse?.tx?.hexStringToByteArray()
    }, { response ->
        val msg = parseJson(response, ErrorResponse::class.java)?.error ?: "Unknown error"
        throw ClientError("Can not fetch transaction: ${response.status} $msg")
    }, true)


    private fun <T> parseJson(response: Response, cls: Class<T>): T? = try {
        val body = BoundedInputStream(response.body.stream, config.maxResponseSize.toLong()).bufferedReader()
        gson.fromJson(body, cls)
    } catch (e: JsonParseException) {
        val rootCause = ExceptionUtils.getRootCause(e)
        if (rootCause is IOException) throw rootCause
        else throw IOException("Json parsing failed", e)
    }

    override fun close() {
        requestStrategy.close()
    }

    /* JSON structures */
    data class Tx(val tx: String)
    data class TxStatus(val status: String?, val rejectReason: String?)
    data class CurrentBlockHeight(val blockHeight: Long)
    data class ErrorResponse(val error: String)
}

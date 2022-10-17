// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.client.core

import mu.KLogging
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.request.Endpoint
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.tx.TransactionStatus
import net.postchain.common.tx.TransactionStatus.*
import net.postchain.crypto.KeyPair
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.make_gtv_gson
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtx.Gtx
import org.http4k.client.ApacheAsyncClient
import org.http4k.client.AsyncHttpHandler
import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.format.Gson.auto
import java.lang.Thread.sleep
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage

data class Tx(val tx: String)
data class TxStatus(val status: String?, val rejectReason: String?)
data class CurrentBlockHeight(val blockHeight: Long)
data class Queries(val queries: List<String>)
data class ErrorResponse(val error: String)

class ConcretePostchainClient(
        override val config: PostchainClientConfig,
        private val httpClient: AsyncHttpHandler = ApacheAsyncClient(),
) : PostchainClient {

    companion object : KLogging()

    private fun nextEndpoint() = config.endpointPool.next()
    private val blockchainRIDHex = config.blockchainRid.toHex()
    private val blockchainRIDOrID = config.queryByChainId?.let { "iid_$it" } ?: blockchainRIDHex
    private val cryptoSystem = config.cryptoSystem
    private val calculator = GtvMerkleHashCalculator(cryptoSystem)

    override fun transactionBuilder() = transactionBuilder(config.signers)

    override fun transactionBuilder(signers: List<KeyPair>) = TransactionBuilder(
            this,
            config.blockchainRid,
            signers.map { it.pubKey.data },
            signers.map { it.sigMaker(cryptoSystem) },
            cryptoSystem
    )


    override fun querySync(name: String, gtv: Gtv): Gtv {
        try {
            var queryResult: Response? = null
            for (j in 1..config.endpointPool.size()) {
                val endpoint = nextEndpoint()
                val request = createQueryRequest(name, gtv, endpoint)
                endpoint@ for (i in 1..config.failOverConfig.attemptsPerEndpoint) {
                    queryResult = queryTo(request, endpoint).toCompletableFuture().join()
                    when (queryResult.status) {
                        Status.OK -> return queryResponseToGtv(queryResult)
                        Status.BAD_REQUEST -> throw buildException(queryResult)
                        Status.INTERNAL_SERVER_ERROR -> throw buildException(queryResult)
                        Status.NOT_FOUND -> throw buildException(queryResult)
                        Status.UNKNOWN_HOST -> break@endpoint
                        Status.SERVICE_UNAVAILABLE -> break@endpoint
                    }
                    sleep(config.failOverConfig.attemptInterval.toMillis())
                }
            }
            throw buildException(queryResult!!)
        } catch (e: CompletionException) {
            throw e.cause ?: e
        }
    }

    override fun query(name: String, gtv: Gtv): CompletionStage<Gtv> {
        val endpoint = nextEndpoint()
        val request = createQueryRequest(name, gtv, endpoint)
        return queryTo(request, endpoint).thenApply {
            if (it.status != Status.OK) {
                throw buildException(it)
            }
            queryResponseToGtv(it)
        }
    }

    private fun queryResponseToGtv(response: Response): Gtv {
        if (response.body == Body.EMPTY) return GtvNull
        val respList = Body.auto<List<String>>().toLens()(response)
        return GtvFactory.decodeGtv(respList.first().hexStringToByteArray())
    }

    private fun createQueryRequest(
            name: String,
            gtv: Gtv,
            endpoint: Endpoint,
    ): Request {
        val queriesLens = Body.auto<Queries>().toLens()
        val gtxQuery = gtv(gtv(name), gtv)
        val encodedQuery = GtvEncoder.encodeGtv(gtxQuery).toHex()
        return queriesLens(
                Queries(listOf(encodedQuery)),
                Request(Method.POST, "${endpoint.url}/query_gtx/$blockchainRIDOrID")
        )
    }

    private fun queryTo(request: Request, endpoint: Endpoint): CompletionStage<Response> {
        val result = CompletableFuture<Response>()
        httpClient(request) { response ->
            if (response.status == Status.SERVICE_UNAVAILABLE) endpoint.setUnreachable()
            result.complete(response)
        }
        return result
    }

    private fun buildException(response: Response): UserMistake {
        val msg = if (response.body == Body.EMPTY) "" else Body.auto<ErrorResponse>().toLens()(response).error
        return UserMistake("Can not make a query: ${response.status} $msg")
    }


    override fun currentBlockHeight(): CompletionStage<Long> {
        val currentBlockHeightLens = Body.auto<CurrentBlockHeight>().toLens()
        val endpoint = nextEndpoint()
        val request = Request(Method.GET, "${endpoint.url}/node/$blockchainRIDOrID/height")
        return queryTo(request, endpoint).thenApply {
            if (it.status != Status.OK) throw buildException(it)
            currentBlockHeightLens(it).blockHeight
        }
    }

    override fun currentBlockHeightSync(): Long = try {
        currentBlockHeight().toCompletableFuture().join()
    } catch (e: CompletionException) {
        throw e.cause ?: e
    }

    override fun blockAtHeight(height: Long): CompletionStage<Gtv> {
        val endpoint = nextEndpoint()
        val request = Request(Method.GET, "${endpoint.url}/blocks/$blockchainRIDOrID/height/$height")
        return queryTo(request, endpoint).thenApply {
            if (it.status != Status.OK) throw buildException(it)
            val json = Body.auto<String>().toLens()(it)
            make_gtv_gson().fromJson(json, Gtv::class.java)
        }
    }

    override fun blockAtHeightSync(height: Long): Gtv = try {
        blockAtHeight(height).toCompletableFuture().join()
    } catch (e: CompletionException) {
        throw e.cause ?: e
    }

    override fun postTransactionSync(tx: Gtx): TransactionResult {
        try {
            var result: TransactionResult? = null
            for (j in 1..config.endpointPool.size()) {
                val endpoint = nextEndpoint()
                endpoint@ for (i in 1..config.failOverConfig.attemptsPerEndpoint) {
                    result = postTransactionTo(tx, endpoint).toCompletableFuture().join()
                    when (result.httpStatusCode) {
                        Status.OK.code, Status.BAD_REQUEST.code, Status.NOT_FOUND.code, Status.CONFLICT.code -> return result
                        Status.SERVICE_UNAVAILABLE.code -> break@endpoint
                    }
                    sleep(config.failOverConfig.attemptInterval.toMillis())
                }
            }
            return result!!
        } catch (e: CompletionException) {
            throw e.cause ?: e
        }
    }

    override fun postTransaction(tx: Gtx): CompletionStage<TransactionResult> {
        return postTransactionTo(tx, nextEndpoint())
    }

    private fun postTransactionTo(tx: Gtx, endpoint: Endpoint): CompletableFuture<TransactionResult> {
        val txLens = Body.auto<Tx>().toLens()
        val txRid = TxRid(tx.calculateTxRid(calculator).toHex())
        val encodedTx = Tx(tx.encodeHex())
        val request = txLens(encodedTx, Request(Method.POST, "${endpoint.url}/tx/$blockchainRIDHex"))
        val result = CompletableFuture<TransactionResult>()
        httpClient(request) { response ->
            if (response.status == Status.SERVICE_UNAVAILABLE) endpoint.setUnreachable()
            val status = if (response.status == Status.OK) WAITING else REJECTED
            result.complete(TransactionResult(txRid, status, response.status.code, response.status.description))
        }
        return result
    }

    override fun postTransactionSyncAwaitConfirmation(tx: Gtx): TransactionResult {
        val resp = postTransactionSync(tx)
        if (resp.status == REJECTED) {
            return resp
        }
        return awaitConfirmation(resp.txRid, config.statusPollCount, config.statusPollInterval)
    }

    override fun awaitConfirmation(txRid: TxRid, retries: Int, pollInterval: Duration): TransactionResult {
        var lastKnownTxResult = TransactionResult(txRid, UNKNOWN, null, null)
        // keep polling till getting Confirmed or Rejected
        run poll@{
            repeat(retries) {
                try {
                    val deferredPollResult = checkTxStatus(txRid)
                    lastKnownTxResult = deferredPollResult.toCompletableFuture().join()
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

    override fun checkTxStatus(txRid: TxRid): CompletionStage<TransactionResult> {
        val txStatusLens = Body.auto<TxStatus>().toLens()
        val endpoint = nextEndpoint()
        val validationRequest = Request(Method.GET, "${endpoint.url}/tx/$blockchainRIDOrID/${txRid.rid}/status")
        return queryTo(validationRequest, endpoint).thenApply { response ->
            val txStatus = txStatusLens(response)
            TransactionResult(
                    txRid,
                    TransactionStatus.valueOf(txStatus.status?.uppercase() ?: "UNKNOWN"),
                    response.status.code,
                    txStatus.rejectReason
            )
        }
    }
}

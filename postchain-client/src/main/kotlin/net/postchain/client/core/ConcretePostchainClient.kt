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
import net.postchain.common.tx.TransactionStatus.CONFIRMED
import net.postchain.common.tx.TransactionStatus.REJECTED
import net.postchain.common.tx.TransactionStatus.UNKNOWN
import net.postchain.common.tx.TransactionStatus.WAITING
import net.postchain.crypto.KeyPair
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtx.Gtx
import org.http4k.client.ApacheAsyncClient
import org.http4k.client.AsyncHttpHandler
import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.format.Gson.auto
import java.lang.Thread.sleep
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
    private val httpClient: AsyncHttpHandler = ApacheAsyncClient()
) : PostchainClient {

    companion object : KLogging()

    private fun nextEndpoint() = config.endpointPool.next()
    private val blockchainRIDHex = config.blockchainRid.toHex()
    private val cryptoSystem = config.cryptoSystem
    private val calculator = GtvMerkleHashCalculator(cryptoSystem)

    override fun transactionBuilder() = transactionBuilder(config.signers)

    override fun transactionBuilder(signers: List<KeyPair>) = TransactionBuilder(
        this,
        config.blockchainRid,
        signers.map { it.pubKey.key },
        signers.map { it.sigMaker(cryptoSystem) },
        cryptoSystem
    )


    override fun query(name: String, gtv: Gtv): CompletionStage<Gtv> {
        val queriesLens = Body.auto<Queries>().toLens()
        val gtxQuery = gtv(gtv(name), gtv)
        val encodedQuery = GtvEncoder.encodeGtv(gtxQuery).toHex()
        val endpoint = nextEndpoint()
        val request = queriesLens(
            Queries(listOf(encodedQuery)),
            Request(Method.POST, "${endpoint.url}/query_gtx/$blockchainRIDHex")
        )

        val result = CompletableFuture<Gtv>()
        httpClient(request) { response ->
            if (response.status.code == 503) endpoint.setUnreachable()
            if (response.status != Status.OK) {
                val msg = if (response.body == Body.EMPTY) "" else Body.auto<ErrorResponse>().toLens()(response).error
                result.completeExceptionally(UserMistake("Can not make query_gtx api call: ${response.status} $msg"))
                return@httpClient
            }
            if (response.body == Body.EMPTY) result.complete(GtvNull) && return@httpClient
            val respList = Body.auto<List<String>>().toLens()(response)
            result.complete(GtvFactory.decodeGtv(respList.first().hexStringToByteArray()))
        }
        return result
    }

    override fun querySync(name: String, gtv: Gtv): Gtv = try {
        query(name, gtv).toCompletableFuture().join()
    } catch (e: CompletionException) {
        throw e.cause ?: e
    }

    override fun currentBlockHeight(): CompletionStage<Long> {
        val currentBlockHeightLens = Body.auto<CurrentBlockHeight>().toLens()
        val endpoint = nextEndpoint()
        val result = CompletableFuture<Long>()
        httpClient(Request(Method.GET, "${endpoint.url}/node/$blockchainRIDHex/height")) { response ->
            if (response.status.code == 503) endpoint.setUnreachable()
            if (response.status != Status.OK) {
                val msg = if (response.body == Body.EMPTY) "" else Body.auto<ErrorResponse>().toLens()(response).error
                result.completeExceptionally(UserMistake("Can not make node/height api call: ${response.status} $msg"))
                return@httpClient
            }
            result.complete(currentBlockHeightLens(response).blockHeight)
        }
        return result
    }

    override fun currentBlockHeightSync(): Long = try {
        currentBlockHeight().toCompletableFuture().join()
    } catch (e: CompletionException) {
        throw e.cause ?: e
    }

    override fun postTransactionSync(tx: Gtx): TransactionResult {
        try {
            var result: TransactionResult? = null
            for (j in 1..config.endpointPool.size()) {
                val endpoint = nextEndpoint()
                for (i in 1..config.failOverConfig.attemptsPerEndpoint) {
                    result = postTransactionTo(tx, endpoint).toCompletableFuture().join()
                    if (result.status == CONFIRMED) return result
                    if (result.httpStatusCode == 400) return result
                    if (result.httpStatusCode == 409) return result
                    sleep(config.failOverConfig.attemptInterval)
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
            if (response.status.code == 503) endpoint.setUnreachable()
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

    override fun awaitConfirmation(txRid: TxRid, retries: Int, pollInterval: Long): TransactionResult {
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
                sleep(pollInterval)
            }
        }
        return lastKnownTxResult
    }

    override fun checkTxStatus(txRid: TxRid): CompletionStage<TransactionResult> {
        val txStatusLens = Body.auto<TxStatus>().toLens()
        val endpoint = nextEndpoint()
        val validationRequest = Request(Method.GET, "${endpoint.url}/tx/$blockchainRIDHex/${txRid.rid}/status")
        val result = CompletableFuture<TransactionResult>()
        httpClient(validationRequest) { response ->
            if (response.status.code == 503) endpoint.setUnreachable()
            val txStatus = txStatusLens(response)
            result.complete(
                TransactionResult(
                    txRid,
                    TransactionStatus.valueOf(txStatus.status?.uppercase() ?: "UNKNOWN"),
                    response.status.code,
                    txStatus.rejectReason
                )
            )
        }
        return result
    }
}

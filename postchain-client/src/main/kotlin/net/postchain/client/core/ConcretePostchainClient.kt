// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.client.core

import mu.KLogging
import net.postchain.client.config.STATUS_POLL_COUNT
import net.postchain.client.config.STATUS_POLL_INTERVAL
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.tx.TransactionStatus
import net.postchain.common.tx.TransactionStatus.CONFIRMED
import net.postchain.common.tx.TransactionStatus.REJECTED
import net.postchain.common.tx.TransactionStatus.UNKNOWN
import net.postchain.common.tx.TransactionStatus.WAITING
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtx.data.GTXDataBuilder
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
data class TxStatus(val status: String?)
data class Queries(val queries: List<String>)
data class ErrorResponse(val error: String)

class ConcretePostchainClient(
    private val resolver: PostchainNodeResolver,
    private val blockchainRID: BlockchainRid,
    private val defaultSigner: DefaultSigner?,
    private val statusPollCount: Int = STATUS_POLL_COUNT,
    private val statusPollInterval: Long = STATUS_POLL_INTERVAL,
    private val client: AsyncHttpHandler = ApacheAsyncClient()
) : PostchainClient {

    companion object : KLogging()

    private val serverUrl = resolver.getNodeURL(blockchainRID)
    private val blockchainRIDHex = blockchainRID.toHex()

    override fun makeTransaction(): GTXTransactionBuilder {
        return GTXTransactionBuilder(this, blockchainRID, defaultSigner?.let { arrayOf(it.pubkey) } ?: arrayOf())
    }

    override fun makeTransaction(signers: Array<ByteArray>): GTXTransactionBuilder {
        return GTXTransactionBuilder(this, blockchainRID, signers)
    }


    override fun query(name: String, gtv: Gtv): CompletionStage<Gtv> {
        val queriesLens = Body.auto<Queries>().toLens()
        val gtxQuery = gtv(gtv(name), gtv)
        val encodedQuery = GtvEncoder.encodeGtv(gtxQuery).toHex()
        val request = queriesLens(
            Queries(listOf(encodedQuery)),
            Request(Method.POST, "$serverUrl/query_gtx/$blockchainRIDHex")
        )

        val r = CompletableFuture<Gtv>()
        client(request) { res ->
            if (res.status != Status.OK) {
                val msg = if (res.body == Body.EMPTY) "" else Body.auto<ErrorResponse>().toLens()(res).error
                r.completeExceptionally(UserMistake("Can not make query_gtx api call: ${res.status} $msg"))
                return@client
            }
            if (res.body == Body.EMPTY) r.complete(GtvNull) && return@client
            val respList = Body.auto<List<String>>().toLens()(res)
            r.complete(GtvFactory.decodeGtv(respList.first().hexStringToByteArray()))
        }
        return r
    }

    override fun querySync(name: String, gtv: Gtv): Gtv = try {
        query(name, gtv).toCompletableFuture().join()
    } catch (e: CompletionException) {
        throw e.cause ?: e
    }

    override fun postTransactionSync(txBuilder: GTXDataBuilder): TransactionResult = try {
        postTransaction(txBuilder).toCompletableFuture().join()
    } catch (e: CompletionException) {
        throw e.cause ?: e
    }

    override fun postTransaction(txBuilder: GTXDataBuilder): CompletionStage<TransactionResult> {
        if (!txBuilder.finished) txBuilder.finish()
        val txLens = Body.auto<Tx>().toLens()
        val txRid = TxRid(txBuilder.getDigestForSigning().toHex())
        val tx = Tx(txBuilder.serialize().toHex())
        val request = txLens(tx, Request(Method.POST, "$serverUrl/tx/$blockchainRIDHex"))
        val result = CompletableFuture<TransactionResult>()
        client(request) { resp ->
            val status = if (resp.status == Status.OK) WAITING else REJECTED
            result.complete(TransactionResult(txRid, status, resp.status.code, resp.status.description))
        }
        return result
    }

    override fun postTransactionSyncAwaitConfirmation(txBuilder: GTXDataBuilder): TransactionResult {
        val resp = postTransactionSync(txBuilder)
        if (resp.status == REJECTED) {
            return resp
        }
        return awaitConfirmation(resp.txRid, statusPollCount, statusPollInterval)
    }

    override fun awaitConfirmation(txRid: TxRid, retries: Int, pollInterval: Long): TransactionResult {
        var lastKnownTxResult = TransactionResult(txRid, UNKNOWN, null, null)
        // keep polling till getting Confirmed or Rejected
        repeat(retries) {
            try {
                val deferredPollResult = checkTxStatus(txRid)
                lastKnownTxResult = deferredPollResult.toCompletableFuture().join()
                if (lastKnownTxResult.status == CONFIRMED || lastKnownTxResult.status == REJECTED) return@repeat
            } catch (e: Exception) {
                logger.warn(e) { "Unable to poll for new block" }
                lastKnownTxResult = TransactionResult(txRid, UNKNOWN, null, null)
            }
            sleep(pollInterval)
        }
        return lastKnownTxResult
    }

    override fun checkTxStatus(txRid: TxRid): CompletionStage<TransactionResult> {
        val txStatusLens = Body.auto<TxStatus>().toLens()
        val validationRequest = Request(Method.GET, "$serverUrl/tx/$blockchainRIDHex/${txRid.rid}/status")
        val result = CompletableFuture<TransactionResult>()
        client(validationRequest) { response ->
            val status =
                TransactionStatus.valueOf(txStatusLens(response).status?.uppercase() ?: "UNKNOWN")
            result.complete(
                TransactionResult(
                    txRid,
                    status,
                    response.status.code,
                    response.status.description
                )
            )
        }
        return result
    }
}
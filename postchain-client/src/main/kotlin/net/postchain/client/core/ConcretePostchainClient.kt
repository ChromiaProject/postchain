// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.client.core

import mu.KLogging
import net.postchain.client.config.STATUS_POLL_COUNT
import net.postchain.client.config.STATUS_POLL_INTERVAL
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.tx.TransactionStatus
import net.postchain.common.tx.TransactionStatus.CONFIRMED
import net.postchain.common.tx.TransactionStatus.REJECTED
import net.postchain.common.tx.TransactionStatus.UNKNOWN
import net.postchain.common.tx.TransactionStatus.WAITING
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvFactory.gtv
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
    private val cryptoSystem = Secp256K1CryptoSystem()
    private val calculator = GtvMerkleHashCalculator(cryptoSystem)

    override fun makeTransaction() = makeTransaction(defaultSigner?.let { listOf(it.pubkey) } ?: listOf())

    override fun makeTransaction(signers: List<ByteArray>) = TransactionBuilder(this, blockchainRID, signers, listOf(), cryptoSystem)


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
                val error = Body.auto<ErrorResponse>().toLens()(res)
                r.completeExceptionally(UserMistake("Can not make query_gtx api call: ${res.status} ${error.error}"))
            }
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

    override fun postTransactionSync(tx: Gtx): TransactionResult = try {
        postTransaction(tx).toCompletableFuture().join()
    } catch (e: CompletionException) {
        throw e.cause ?: e
    }

    override fun postTransaction(tx: Gtx): CompletionStage<TransactionResult> {
        val txLens = Body.auto<Tx>().toLens()
        val txRid = TxRid(tx.calculateTxRid(calculator).toHex())
        val encodedTx = Tx(tx.encode().toHex())
        val request = txLens(encodedTx, Request(Method.POST, "$serverUrl/tx/$blockchainRIDHex"))
        val result = CompletableFuture<TransactionResult>()
        client(request) { resp ->
            val status = if (resp.status == Status.OK) WAITING else REJECTED
            result.complete(TransactionResult(txRid, status, resp.status.code, resp.status.description))
        }
        return result
    }

    override fun postTransactionSyncAwaitConfirmation(tx: Gtx): TransactionResult {
        val resp = postTransactionSync(tx)
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
// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.client.core

import mu.KLogging
import net.postchain.client.core.TransactionStatus.CONFIRMED
import net.postchain.client.core.TransactionStatus.REJECTED
import net.postchain.client.core.TransactionStatus.WAITING
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.GTXDataBuilder
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import org.http4k.client.JavaHttpClient
import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.format.Gson.auto
import java.lang.Thread.sleep

data class Tx(val tx: String)
data class TxStatus(val status: String)
data class Queries(val queries: List<String>)
data class ErrorResponse(val error: String)

class ConcretePostchainClient(
        private val resolver: PostchainNodeResolver,
        private val blockchainRID: BlockchainRid,
        private val defaultSigner: DefaultSigner?
) : PostchainClient {

    companion object : KLogging()

    private val serverUrl = resolver.getNodeURL(blockchainRID)
    private val client = JavaHttpClient()
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
        val queriesLens = Body.auto<Queries>().toLens()
        val gtxQuery = gtv(gtv(name), gtv)
        val encodedQuery = GtvEncoder.encodeGtv(gtxQuery).toHex()
        val request = queriesLens(
                Queries(listOf(encodedQuery)),
                Request(Method.POST, "$serverUrl/query_gtx/$blockchainRIDHex")
        )

        val res = client(request)
        if (res.status != Status.OK) {
            val error = Body.auto<ErrorResponse>().toLens()(res)
            throw UserMistake("Can not make query_gtx api call: ${res.status} ${error.error}")
        }
        val respList = Body.auto<List<String>>().toLens()(res)
        return GtvFactory.decodeGtv(respList.first().hexStringToByteArray())
    }

    private fun doPostTransaction(txBuilder: GTXDataBuilder, confirmationLevel: ConfirmationLevel): TransactionResult {
        val txLens = Body.auto<Tx>().toLens()
        val tx = Tx(txBuilder.serialize().toHex())
        val request = txLens(tx, Request(Method.POST, "$serverUrl/tx/$blockchainRIDHex"))
        when (confirmationLevel) {

            ConfirmationLevel.NO_WAIT -> {
                val resp = client(request)
                return if (resp.status == Status.OK) {
                    TransactionResultImpl(WAITING)
                } else {
                    println(resp)
                    TransactionResultImpl(REJECTED)
                }
            }

            ConfirmationLevel.UNVERIFIED -> {
                val resp = client(request)
                if (resp.status.clientError) {
                    return TransactionResultImpl(REJECTED)
                }

                val txHashHex = txBuilder.getDigestForSigning().toHex()
                val txStatusLens = Body.auto<TxStatus>().toLens()
                val validationRequest = Request(Method.GET, "$serverUrl/tx/$blockchainRIDHex/$txHashHex/status")

                // keep polling till getting Confirmed or Rejected
                repeat(retrieveTxStatusAttempts) {
                    try {
                        txStatusLens(client(validationRequest)).let { statusResponse ->
                            when (statusResponse.status) {
                                CONFIRMED.status -> return TransactionResultImpl(CONFIRMED)
                                REJECTED.status -> return TransactionResultImpl(REJECTED)
                                else -> sleep(retrieveTxStatusIntervalMs)
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn(e) { "Unable to poll for new block" }
                        sleep(retrieveTxStatusIntervalMs)
                    }
                }

                return TransactionResultImpl(REJECTED)
            }

            else -> {
                return TransactionResultImpl(REJECTED)
            }
        }
    }
}
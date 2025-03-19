package net.postchain.managed

import mu.KLogging
import net.postchain.api.rest.model.ApiStatus
import net.postchain.api.rest.statusBody
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.common.tx.TransactionStatus
import net.postchain.core.Transaction
import net.postchain.ebft.worker.TransactionForwarder
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import org.apache.commons.io.input.BoundedInputStream
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.hc.core5.http.HttpHeaders.ACCEPT
import org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.MemoryBody
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import java.io.EOFException
import java.io.IOException
import java.util.zip.GZIPInputStream
import kotlin.random.Random

class BaseTransactionForwarder(
        private val client: HttpHandler,
        private val dataSource: ManagedNodeDataSource,
        private val blockchainRid: BlockchainRid,
        private val random: Random
) : TransactionForwarder {
    companion object : KLogging()

    override fun forward(tx: Transaction) {
        val apiUrls = dataSource.getBlockchainApiUrls(blockchainRid)
        val randomizedApiUrls = apiUrls.shuffled(random)

        for (apiUrl in randomizedApiUrls) {
            logger.debug { "Forwarding transaction ${tx.getRID().toHex()} to $apiUrl" }
            val response: Response = client(
                    Request(Method.POST, "$apiUrl/tx/$blockchainRid")
                            .header(CONTENT_TYPE, ContentType.OCTET_STREAM.value)
                            .header(ACCEPT, ContentType.OCTET_STREAM.value)
                            .body(MemoryBody(tx.getRawData())))
            if (response.status.successful) return
            logger.info { "Unable to forward transaction ${tx.getRID().toHex()} to $apiUrl : ${response.status.code} ${parseErrorResponse(response)}" }
        }
        throw UserMistake("Unable to forward transaction ${tx.getRID().toHex()} to any signer node")
    }

    override fun checkStatus(tx: Transaction): ApiStatus {
        val apiUrls = dataSource.getBlockchainApiUrls(blockchainRid)
        val randomizedApiUrls = apiUrls.shuffled(random)

        for (apiUrl in randomizedApiUrls) {
            val response: Response = client(
                    Request(Method.GET, "$apiUrl/tx/$blockchainRid/${tx.getRID().toHex()}/status")
                            .header(ACCEPT, ContentType.APPLICATION_JSON.value))
            if (response.status.successful) {
                val apiStatus = statusBody(response)
                if (apiStatus.status != TransactionStatus.UNKNOWN.status) {
                    return apiStatus
                }
            } else {
                val errorMessage = parseErrorResponse(response)
                logger.info { "Unable to check transaction status ${tx.getRID().toHex()} from $apiUrl : ${response.status.code} $errorMessage" }
            }
        }
        logger.info { "Unable to get transaction status for ${tx.getRID().toHex()} from any signer node" }
        return ApiStatus(TransactionStatus.UNKNOWN)
    }


    // Copied from https://gitlab.com/chromaway/core/postchain-client/blob/69cceb248817a2e6961e5546b05aa9f4f310934f/postchain-client/src/main/kotlin/net/postchain/client/impl/PostchainClientImpl.kt#L411

    private fun parseErrorResponse(response: Response): String {
        val responseStream = responseStream(response)
        val contentType = response.header(CONTENT_TYPE) ?: ""
        return when {
            contentType == ContentType.OCTET_STREAM.value ->
                decodeGtv(responseStream)?.asString() ?: response.status.description

            else -> {
                val responseBody = responseStream(response).use { it.readAllBytes() }
                if (responseBody.isNotEmpty()) String(responseBody) else response.status.description
            }
        }
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

    private fun responseStream(response: Response): BoundedInputStream {
        val originalStream = if (response.header("content-encoding") == "gzip") {
            GZIPInputStream(response.body.stream)
        } else {
            response.body.stream
        }
        return BoundedInputStream.builder()
                .setInputStream(originalStream)
                .setMaxCount(64 * 1024)
                .setPropagateClose(true)
                .get()
    }
}

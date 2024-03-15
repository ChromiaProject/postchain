package net.postchain.network.mastersub

import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.core.block.BlockDetail
import net.postchain.gtv.Gtv
import net.postchain.network.mastersub.protocol.MsBlockAtHeightRequest
import net.postchain.network.mastersub.protocol.MsBlockAtHeightResponse
import net.postchain.network.mastersub.protocol.MsBlocksFromHeightRequest
import net.postchain.network.mastersub.protocol.MsMessage
import net.postchain.network.mastersub.protocol.MsQueryFailure
import net.postchain.network.mastersub.protocol.MsQueryRequest
import net.postchain.network.mastersub.protocol.MsQueryResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Suppress("UNCHECKED_CAST")
class MasterSubQueryManager(private val queryTimeoutMs: Long, private val messageSender: (BlockchainRid?, MsMessage) -> Boolean) : MsMessageHandler {

    companion object : KLogging()

    private val requestCounter = AtomicLong(0L)
    private val outstandingRequests = ConcurrentHashMap<Long, CompletableFuture<Any?>>()

    fun query(targetBlockchainRid: BlockchainRid?, name: String, args: Gtv): CompletionStage<Gtv> =
            sendRequest(targetBlockchainRid, { requestId ->
                MsQueryRequest(
                        requestId,
                        targetBlockchainRid,
                        name,
                        args
                )
            }, "Unable to send query")

    fun blockAtHeight(targetBlockchainRid: BlockchainRid, height: Long): CompletionStage<BlockDetail?> =
        sendRequest(targetBlockchainRid, { requestId ->
            MsBlockAtHeightRequest(
                    requestId,
                    targetBlockchainRid,
                    height
            )
        }, "Unable to send block at height query")

    fun blocksFromHeight(targetBlockchainRid: BlockchainRid, fromHeight: Long, limit: Long, txHashesOnly: Boolean): CompletionStage<List<BlockDetail>> =
        sendRequest(targetBlockchainRid, { requestId ->
            MsBlocksFromHeightRequest(
                    requestId,
                    targetBlockchainRid,
                    fromHeight,
                    limit,
                    txHashesOnly
            )
        }, "Unable to send blocks from height query")

    private fun <T> sendRequest(targetBlockchainRid: BlockchainRid?, requestProducer: (requestId: Long) -> MsMessage, errorMessage: String): CompletionStage<T> {
        val requestId = requestCounter.incrementAndGet()
        val future = CompletableFuture<Any?>()
        outstandingRequests[requestId] = future

        if (!messageSender(
                        targetBlockchainRid,
                        requestProducer(requestId)
                )) {
            future.completeExceptionally(ProgrammerMistake(errorMessage))
        }
        return future
                .orTimeout(queryTimeoutMs, TimeUnit.MILLISECONDS)
                .whenComplete { _, _ -> outstandingRequests.remove(requestId) } as CompletionStage<T>
    }

    fun isRequestOutstanding(requestId: Long) = outstandingRequests.containsKey(requestId)

    override fun onMessage(message: MsMessage) {
        when (message) {
            is MsQueryResponse -> {
                outstandingRequests[message.requestId]?.complete(message.result)
                        ?: logger.debug { "Got QueryResponse for unknown requestId: ${message.requestId}" }
            }

            is MsBlockAtHeightResponse -> {
                outstandingRequests[message.requestId]?.complete(message.block)
                        ?: logger.debug { "Got BlockAtHeightResponse for unknown requestId: ${message.requestId}" }
            }

            is MsQueryFailure -> {
                outstandingRequests[message.requestId]?.completeExceptionally(UserMistake(message.errorMessage))
                        ?: logger.debug { "Got QueryFailure for unknown requestId: ${message.requestId}" }
            }
        }
    }
}

package net.postchain.network.mastersub

import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.core.block.BlockDetail
import net.postchain.gtv.Gtv
import net.postchain.network.mastersub.protocol.MsBlockAtHeightRequest
import net.postchain.network.mastersub.protocol.MsBlockAtHeightResponse
import net.postchain.network.mastersub.protocol.MsMessage
import net.postchain.network.mastersub.protocol.MsQueryFailure
import net.postchain.network.mastersub.protocol.MsQueryRequest
import net.postchain.network.mastersub.protocol.MsQueryResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

@Suppress("UNCHECKED_CAST")
class MasterSubQueryManager(private val messageSender: (Long, MsMessage) -> Boolean) : MsMessageHandler {

    companion object : KLogging() {
        val timeout = 10.seconds
    }

    private val requestCounter = AtomicLong(0L)
    private val outstandingRequests = ConcurrentHashMap<Long, CompletableFuture<Any?>>()

    fun query(chainId: Long, blockchainRid: BlockchainRid, targetBlockchainRid: BlockchainRid?, name: String, args: Gtv): CompletionStage<Gtv> {
        val requestId = requestCounter.incrementAndGet()
        if (!messageSender(
                        chainId,
                        MsQueryRequest(
                                blockchainRid.data,
                                requestId,
                                targetBlockchainRid,
                                name,
                                args
                        )
                )) {
            return CompletableFuture.failedStage(ProgrammerMistake("Unable to send query"))
        }
        val future = CompletableFuture<Any?>()
                .orTimeout(timeout.inWholeSeconds, TimeUnit.SECONDS)
                .whenComplete { _, _ -> outstandingRequests.remove(requestId) }
        outstandingRequests[requestId] = future
        return future as CompletionStage<Gtv>
    }

    fun blockAtHeight(chainId: Long, blockchainRid: BlockchainRid, targetBlockchainRid: BlockchainRid, height: Long): CompletionStage<BlockDetail?> {
        val requestId = requestCounter.incrementAndGet()
        if (!messageSender(
                        chainId,
                        MsBlockAtHeightRequest(
                                blockchainRid.data,
                                requestId,
                                targetBlockchainRid,
                                height
                        )
                )) {
            return CompletableFuture.failedStage(ProgrammerMistake("Unable to send block at height query"))
        }
        val future = CompletableFuture<Any?>()
                .orTimeout(timeout.inWholeSeconds, TimeUnit.SECONDS)
                .whenComplete { _, _ -> outstandingRequests.remove(requestId) }
        outstandingRequests[requestId] = future
        return future as CompletionStage<BlockDetail?>
    }

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

package net.postchain.network.mastersub.subnode

import mu.KLogging
import net.postchain.common.exception.UserMistake
import net.postchain.concurrent.util.whenCompleteUnwrapped
import net.postchain.core.block.BlockQueriesProvider
import net.postchain.network.mastersub.MsMessageHandler
import net.postchain.network.mastersub.protocol.MsBlockAtHeightRequest
import net.postchain.network.mastersub.protocol.MsBlockAtHeightResponse
import net.postchain.network.mastersub.protocol.MsMessage
import net.postchain.network.mastersub.protocol.MsQueryFailure
import net.postchain.network.mastersub.protocol.MsQueryRequest
import net.postchain.network.mastersub.protocol.MsQueryResponse
import java.util.concurrent.CompletableFuture

class SubQueryHandler(private val chainId: Long,
                      private val blockQueriesProvider: BlockQueriesProvider,
                      private val subConnectionManager: SubConnectionManager) : MsMessageHandler {

    companion object : KLogging()

    override fun onMessage(message: MsMessage) {
        when (message) {
            is MsQueryRequest -> {
                logger.trace { "Received query from master with target blockchain-rid ${message.targetBlockchainRid} and request id ${message.requestId}" }
                val blockQueries = message.targetBlockchainRid?.let { blockQueriesProvider.getBlockQueries(it) }
                (blockQueries?.query(message.name, message.args)
                        ?: CompletableFuture.failedFuture(UserMistake("blockchain ${message.targetBlockchainRid} not found")))
                        .whenCompleteUnwrapped(onSuccess = { response ->
                            logger.trace { "Sending response to master for request-id ${message.requestId}" }
                            subConnectionManager.sendMessageToMaster(chainId, MsQueryResponse(
                                    message.requestId,
                                    response
                            ))
                        }, onError = { exception ->
                            logger.trace { "Sending failure to master for request-id ${message.requestId}" }
                            subConnectionManager.sendMessageToMaster(chainId, MsQueryFailure(
                                    message.requestId,
                                    exception.toString()
                            ))
                        })
            }

            is MsBlockAtHeightRequest -> {
                val blockQueries = blockQueriesProvider.getBlockQueries(message.targetBlockchainRid)
                (blockQueries?.getBlockRid(message.height)?.thenCompose {
                    if (it == null) {
                        CompletableFuture.completedFuture(null)
                    } else {
                        blockQueries.getBlock(it, true)
                    }
                } ?: CompletableFuture.failedFuture(UserMistake("blockchain ${message.targetBlockchainRid} not found")))
                        .whenCompleteUnwrapped(onSuccess = { response ->
                            subConnectionManager.sendMessageToMaster(chainId, MsBlockAtHeightResponse(
                                    message.requestId,
                                    response
                            ))
                        }, onError = { exception ->
                            subConnectionManager.sendMessageToMaster(chainId, MsQueryFailure(
                                    message.requestId,
                                    exception.toString()
                            ))
                        })
            }
        }
    }
}

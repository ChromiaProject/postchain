package net.postchain.network.mastersub.subnode

import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.concurrent.util.whenCompleteUnwrapped
import net.postchain.core.block.BlockQueries
import net.postchain.network.mastersub.MsMessageHandler
import net.postchain.network.mastersub.protocol.MsBlockAtHeightRequest
import net.postchain.network.mastersub.protocol.MsBlockAtHeightResponse
import net.postchain.network.mastersub.protocol.MsBlocksFromHeightRequest
import net.postchain.network.mastersub.protocol.MsBlocksFromHeightResponse
import net.postchain.network.mastersub.protocol.MsMessage
import net.postchain.network.mastersub.protocol.MsQueryFailure
import net.postchain.network.mastersub.protocol.MsQueryRequest
import net.postchain.network.mastersub.protocol.MsQueryResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class SubQueryHandler(private val chainId: Long,
                      private val blockchainRid: BlockchainRid,
                      private val blockQueries: BlockQueries,
                      private val subConnectionManager: SubConnectionManager) : MsMessageHandler {

    companion object : KLogging()

    override fun onMessage(message: MsMessage) {
        when (message) {
            is MsQueryRequest -> {
                logger.trace { "Received query from master with target blockchain-rid ${message.targetBlockchainRid} and request id ${message.requestId}" }
                buildQuery(message.requestId, message.targetBlockchainRid, ::MsQueryResponse) {
                    blockQueries.query(message.name, message.args)
                }
            }

            is MsBlockAtHeightRequest -> {
                buildQuery(message.requestId, message.targetBlockchainRid, ::MsBlockAtHeightResponse) {
                    blockQueries.getBlockRid(message.height).thenCompose {
                        if (it == null) {
                            CompletableFuture.completedFuture(null)
                        } else {
                            blockQueries.getBlock(it, true)
                        }
                    }
                }
            }

            is MsBlocksFromHeightRequest -> {
                buildQuery(message.requestId, message.targetBlockchainRid, ::MsBlocksFromHeightResponse) {
                    blockQueries.getBlocksFromHeight(message.fromHeight, message.limit.toInt())
                }
            }
        }
    }

    private fun <T> buildQuery(
            requestId: Long,
            targetBlockchainRid: BlockchainRid?,
            responseBuilder: (requestId: Long, result: T) -> MsMessage,
            blockQuery: () -> CompletionStage<T>,
    ) {
        if (targetBlockchainRid != blockchainRid) {
            subConnectionManager.sendMessageToMaster(
                    chainId,
                    MsQueryFailure(
                            requestId,
                            "Target blockchain-rid mismatch: $targetBlockchainRid != $blockchainRid"
                    )
            )
            return
        }

        blockQuery().whenCompleteUnwrapped(onSuccess = { response ->
                    logger.trace { "Sending response to master for request-id $requestId" }
                    subConnectionManager.sendMessageToMaster(chainId, responseBuilder(requestId, response))
                }, onError = { exception ->
                    logger.trace { "Sending failure to master for request-id $requestId" }
                    subConnectionManager.sendMessageToMaster(chainId, MsQueryFailure(
                            requestId,
                            exception.toString()
                    ))
                })
    }
}

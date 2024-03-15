package net.postchain.network.mastersub.master

import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.concurrent.util.whenCompleteUnwrapped
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.BlockQueriesProvider
import net.postchain.managed.ManagedNodeDataSource
import net.postchain.network.mastersub.MasterSubQueryManager
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

class MasterQueryHandler(
        private val respondToQuery: (MsMessage) -> Unit,
        private val masterSubQueryManager: MasterSubQueryManager,
        private val dataSource: ManagedNodeDataSource,
        private val blockQueriesProvider: BlockQueriesProvider
) : MsMessageHandler {

    companion object : KLogging()

    override fun onMessage(message: MsMessage) {
        when (message) {
            is MsQueryRequest -> {
                if (message.targetBlockchainRid == null) {
                    try {
                        val response = dataSource.query(message.name, message.args)
                        respondToQuery(MsQueryResponse(
                                message.requestId,
                                response
                        ))
                    } catch (e: Exception) {
                        respondToQuery(MsQueryFailure(
                                message.requestId,
                                e.toString()
                        ))
                    }
                } else {
                    buildQuery(
                            message.requestId,
                            message.targetBlockchainRid,
                            ::MsQueryResponse,
                            { blockQueries -> blockQueries.query(message.name, message.args) },
                            {
                                masterSubQueryManager.query(
                                        message.targetBlockchainRid,
                                        message.name,
                                        message.args)
                            }
                    )
                }
            }

            is MsBlockAtHeightRequest -> {
                buildQuery(
                        message.requestId,
                        message.targetBlockchainRid,
                        ::MsBlockAtHeightResponse,
                        { blockQueries ->
                            blockQueries.getBlockRid(message.height).thenCompose {
                                if (it == null) {
                                    CompletableFuture.completedFuture(null)
                                } else {
                                    blockQueries.getBlock(it, true)
                                }
                            }
                        },
                        {
                            masterSubQueryManager.blockAtHeight(
                                    message.targetBlockchainRid,
                                    message.height
                            )
                        }
                )
            }

            is MsBlocksFromHeightRequest -> {
                buildQuery(
                        message.requestId,
                        message.targetBlockchainRid,
                        ::MsBlocksFromHeightResponse,
                        { it.getBlocksFromHeight(message.fromHeight, message.limit.toInt(), message.txHashesOnly) },
                        {
                            masterSubQueryManager.blocksFromHeight(
                                    message.targetBlockchainRid,
                                message.fromHeight,
                                message.limit,
                                message.txHashesOnly)
                        }
                )
            }
        }
    }

    private fun <T> buildQuery(
            requestId: Long,
            targetBlockchainRid: BlockchainRid?,
            responseBuilder: (requestId: Long, result: T) -> MsMessage,
            blocksQueryBuilder: (BlockQueries) -> CompletionStage<T>,
            masterSubQueryBuilder: () -> CompletionStage<T>,
    ) {
        val blockQueries = targetBlockchainRid?.let { blockQueriesProvider.getBlockQueries(it) }
        if (blockQueries != null) {
            blocksQueryBuilder(blockQueries).whenCompleteUnwrapped(onSuccess = { response ->
                respondToQuery(responseBuilder(
                        requestId,
                        response
                ))
            }, onError = { exception ->
                respondToQuery(MsQueryFailure(
                        requestId,
                        exception.toString()
                ))
            })
        } else {
            logger.trace { "Forwarding message to subnode with target blockchain-rid $targetBlockchainRid and request id $requestId" }
            masterSubQueryBuilder().whenCompleteUnwrapped(onSuccess = { response ->
                logger.trace { "Got response from subnode with target blockchain-rid $targetBlockchainRid and request id $requestId" }
                respondToQuery(responseBuilder(
                        requestId,
                        response))
            }, onError = { error ->
                logger.trace { "Failed to forward request with target blockchain-rid $targetBlockchainRid and request id $requestId to subnode, error: ${error.message}" }
                respondToQuery(MsQueryFailure(
                        requestId,
                        error.toString()
                ))
            })
        }
    }
}
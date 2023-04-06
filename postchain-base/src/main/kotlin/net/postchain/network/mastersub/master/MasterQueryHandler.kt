package net.postchain.network.mastersub.master

import mu.KLogging
import net.postchain.concurrent.util.whenCompleteUnwrapped
import net.postchain.core.block.BlockQueriesProvider
import net.postchain.managed.ManagedNodeDataSource
import net.postchain.network.mastersub.MasterSubQueryManager
import net.postchain.network.mastersub.MsMessageHandler
import net.postchain.network.mastersub.protocol.MsBlockAtHeightRequest
import net.postchain.network.mastersub.protocol.MsBlockAtHeightResponse
import net.postchain.network.mastersub.protocol.MsMessage
import net.postchain.network.mastersub.protocol.MsQueryFailure
import net.postchain.network.mastersub.protocol.MsQueryRequest
import net.postchain.network.mastersub.protocol.MsQueryResponse
import java.util.concurrent.CompletableFuture

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
                    val blockQueries = blockQueriesProvider.getBlockQueries(message.targetBlockchainRid)
                    if (blockQueries != null) {
                        blockQueries.query(message.name, message.args).whenCompleteUnwrapped { response, exception ->
                            if (exception == null) {
                                respondToQuery(MsQueryResponse(
                                        message.requestId,
                                        response
                                ))
                            } else {
                                respondToQuery(MsQueryFailure(
                                        message.requestId,
                                        exception.toString()
                                ))
                            }
                        }
                    } else {
                        logger.trace { "Forwarding message to subnode with target blockchain-rid ${message.targetBlockchainRid} and request id ${message.requestId}" }
                        masterSubQueryManager.query(
                                message.targetBlockchainRid,
                                message.name,
                                message.args
                        ).whenCompleteUnwrapped { response, error ->
                            if (error == null) {
                                logger.trace { "Got response from subnode with target blockchain-rid ${message.targetBlockchainRid}and request id ${message.requestId}" }
                                respondToQuery(MsQueryResponse(
                                        message.requestId,
                                        response
                                ))
                            } else {
                                logger.trace { "Failed to forward request with target blockchain-rid ${message.targetBlockchainRid} and request id ${message.requestId} to subnode, error: ${error.message}" }
                                respondToQuery(MsQueryFailure(
                                        message.requestId,
                                        error.toString()
                                ))
                            }
                        }
                    }
                }
            }

            is MsBlockAtHeightRequest -> {
                val blockQueries = blockQueriesProvider.getBlockQueries(message.targetBlockchainRid)
                if (blockQueries != null) {
                    blockQueries.getBlockRid(message.height).thenCompose {
                        if (it == null) {
                            CompletableFuture.completedFuture(null)
                        } else {
                            blockQueries.getBlock(it, true)
                        }
                    }.whenCompleteUnwrapped { response, exception ->
                        if (exception == null) {
                            respondToQuery(MsBlockAtHeightResponse(
                                    message.requestId,
                                    response
                            ))
                        } else {
                            respondToQuery(MsQueryFailure(
                                    message.requestId,
                                    exception.toString()
                            ))
                        }
                    }
                } else {
                    masterSubQueryManager.blockAtHeight(
                            message.targetBlockchainRid,
                            message.height
                    ).whenCompleteUnwrapped { response, error ->
                        if (error == null) {
                            respondToQuery(MsBlockAtHeightResponse(
                                    message.requestId,
                                    response
                            ))
                        } else {
                            respondToQuery(MsQueryFailure(
                                    message.requestId,
                                    error.toString()
                            ))
                        }
                    }
                }
            }
        }
    }
}
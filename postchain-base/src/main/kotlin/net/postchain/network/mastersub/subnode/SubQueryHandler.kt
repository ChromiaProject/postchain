package net.postchain.network.mastersub.subnode

import mu.KLogging
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.block.BlockQueriesProvider
import net.postchain.network.mastersub.MsMessageHandler
import net.postchain.network.mastersub.protocol.MsBlockAtHeightRequest
import net.postchain.network.mastersub.protocol.MsBlockAtHeightResponse
import net.postchain.network.mastersub.protocol.MsMessage
import net.postchain.network.mastersub.protocol.MsQueryFailure
import net.postchain.network.mastersub.protocol.MsQueryRequest
import net.postchain.network.mastersub.protocol.MsQueryResponse
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.thenApply
import nl.komponents.kovenant.unwrap

class SubQueryHandler(private val chainId: Long,
                      private val blockQueriesProvider: BlockQueriesProvider,
                      private val subConnectionManager: SubConnectionManager) : MsMessageHandler {

    companion object : KLogging()

    override fun onMessage(message: MsMessage) {
        when (message) {
            is MsQueryRequest -> {
                logger.trace { "Received query from master with target blockchain-rid ${message.targetBlockchainRid}, message blockchain-rid ${message.blockchainRid.toHex()} and request id ${message.requestId}" }
                val blockQueries = message.targetBlockchainRid?.let { blockQueriesProvider.getBlockQueries(it) }
                (blockQueries?.query(message.name, message.args)
                        ?: Promise.ofFail(UserMistake("blockchain ${message.targetBlockchainRid} not found")))
                        .success {
                            logger.trace { "Sending response to master for request-id ${message.requestId}" }
                            subConnectionManager.sendMessageToMaster(chainId, MsQueryResponse(
                                    message.blockchainRid,
                                    message.requestId,
                                    it
                            ))
                        }
                        .fail {
                            logger.trace { "Sending failure to master for request-id ${message.requestId}" }
                            subConnectionManager.sendMessageToMaster(chainId, MsQueryFailure(
                                    message.blockchainRid,
                                    message.requestId,
                                    it.toString()
                            ))
                        }
            }

            is MsBlockAtHeightRequest -> {
                val blockQueries = blockQueriesProvider.getBlockQueries(message.targetBlockchainRid)
                (blockQueries?.getBlockRid(message.height)?.thenApply {
                    if (this == null) {
                        Promise.ofSuccess(null)
                    } else {
                        blockQueries.getBlock(this, true)
                    }
                }?.unwrap()
                        ?: Promise.ofFail(UserMistake("blockchain ${message.targetBlockchainRid} not found")))
                        .success {
                            subConnectionManager.sendMessageToMaster(chainId, MsBlockAtHeightResponse(
                                    message.blockchainRid,
                                    message.requestId,
                                    it
                            ))
                        }
                        .fail {
                            subConnectionManager.sendMessageToMaster(chainId, MsQueryFailure(
                                    message.blockchainRid,
                                    message.requestId,
                                    it.toString()
                            ))
                        }
            }
        }
    }
}

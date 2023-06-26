// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.peer

import mu.KLogging
import mu.withLoggingContext
import net.postchain.common.BlockchainRid
import net.postchain.common.ExponentialDelay
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.NodeRid
import net.postchain.devtools.NameHelper.peerName
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * A basic implementation of PeersConnectionStrategy. This class assumes that
 * the connectionManager it uses takes care of synchronization.
 *
 * Note: this strategy handles connections to individual peers, which means it needs [PeerConnectionManager].
 */
class DefaultPeersConnectionStrategy(
        val connectionManager: PeerConnectionManager,
        val me: NodeRid,
) : PeersConnectionStrategy {

    private val peerToDelayMap: MutableMap<NodeRid, ExponentialDelay> = mutableMapOf()
    private val timerQueue = ScheduledThreadPoolExecutor(1)

    companion object : KLogging()

    var backupConnTimeMin = 1000
    var backupConnTimeMax = 2000

    var reconnectTimeMin = 100
    var reconnectTimeMax = 1000

    init {
        logger.debug { "${peerName(me)}: new DefaultPeersConnectionStrategy created" } // It's interesting b/c we have the delay map that gets refreshed
    }

    override fun connectAll(chainID: Long, blockchainRid: BlockchainRid, peerIds: Set<NodeRid>) {
        for (peerId in peerIds) {
            if (shouldIConnect(peerId)) {
                connectionManager.connectChainPeer(chainID, peerId)
            }
        }
        timerQueue.schedule({
            withLoggingContext(
                    BLOCKCHAIN_RID_TAG to blockchainRid.toHex(),
                    CHAIN_IID_TAG to chainID.toString()
            ) {
                try {
                    // Connect to all unconnected peers
                    val connectedPeers = connectionManager.getConnectedNodes(chainID)
                    for (wantedPeerId in peerIds) {
                        if (wantedPeerId !in connectedPeers) {
                            connectionManager.connectChainPeer(chainID, wantedPeerId)
                        }
                    }
                } catch (e: ProgrammerMistake) {
                    // This happens if the chain has been disconnected while we waited
                }
            }
        }, Random.nextInt(backupConnTimeMin, backupConnTimeMax).toLong(), TimeUnit.MILLISECONDS)
    }

    /**
     * We'll go for the simplest approach here: Both parties reconnect after a random amount
     * of milliseconds. If they both happen to establish connections at the same time it'll be
     * handled in the duplicateConnectionDetected method.
     *
     * If we'd only reconnect from one party, we might not be able to establish a connection
     * if one party is not aware of the other, for example if peer is a replica. We could use the same technique as
     * in connectAll to solve this, ie use a backup connection after X time. But this would add complexity
     * for little gain over the simplistic approach chosen.
     */
    override fun connectionLost(chainID: Long, blockchainRid: BlockchainRid, peerId: NodeRid, isOutgoing: Boolean) {
        if (connectionManager.isPeerConnected(chainID, peerId)) {
            // There is another connection in use, we should ignore the
            // lost connection
            return
        }
        val delay = peerToDelayMap.computeIfAbsent(peerId) {
            val delayCounterInitialMillis = Random.nextInt(reconnectTimeMin, reconnectTimeMax).toLong()
            ExponentialDelay(delayCounterMillis = delayCounterInitialMillis)
        }

        logger.info { "${peerName(me)}/${chainID}: Reconnecting in ${delay.delayCounterMillis} ms to peer = ${peerName(peerId)}" }
        timerQueue.schedule({
            withLoggingContext(
                    BLOCKCHAIN_RID_TAG to blockchainRid.toHex(),
                    CHAIN_IID_TAG to chainID.toString()
            ) {
                logger.info { "${peerName(me)}/${chainID}: Reconnecting to peer = ${peerName(peerId)}" }
                try {
                    connectionManager.connectChainPeer(chainID, peerId)
                } catch (e: ProgrammerMistake) {
                    // This happens if the chain has been disconnected while we waited
                }
            }
        }, delay.getDelayMillisAndIncrease(), TimeUnit.MILLISECONDS)
    }

    override fun duplicateConnectionDetected(
            chainID: Long,
            isOriginalOutgoing: Boolean,
            peerId: NodeRid,
    ): Boolean {
        return if (shouldIConnect(peerId)) {
            !isOriginalOutgoing
        } else {
            isOriginalOutgoing
        }
    }

    override fun connectionEstablished(chainID: Long, isOutgoing: Boolean, peerId: NodeRid) {
        peerToDelayMap.remove(peerId)
    }

    override fun shutdown() {
        try {
            logger.debug("Shutting down DefaultPeersConnectionStrategy")
            timerQueue.shutdownNow()
            timerQueue.awaitTermination(2000, TimeUnit.MILLISECONDS)
            logger.debug("Shutting down DefaultPeersConnectionStrategy done")
        } catch (e: Exception) {
            logger.debug("Failure when shutting down DefaultPeersConnectionStrategy", e)
        }
    }

    /**
     * This is the main strategy used in this class. A node with higher pubkey will initiate the
     * connection to the peer with lower pubkey.
     */
    private fun shouldIConnect(peer: NodeRid): Boolean {
        return me.toString() > peer.toString()
    }
}
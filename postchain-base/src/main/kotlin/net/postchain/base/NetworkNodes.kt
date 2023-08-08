// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import mu.KLogging
import net.postchain.common.exception.UserMistake
import net.postchain.common.types.WrappedByteArray
import net.postchain.config.app.AppConfig
import net.postchain.core.NodeRid

/**
 * Network nodes can be either signers/block builders or nodes that just want to read your data (= replicas).
 * The purpose of this class is to wrap both these entities
 *
 * The "read-only" nodes do not have to be known by the validator node, but we need some sort of
 * rejection method for these nodes, so they won't ask us too much (DoS attack us).
 *
 * @property myself is this server itself (it can be a signer or a read-only node).
 * @property peerInfoMap keeps track of the OTHER peers (myself not included, if I am a real peer that is)
 * @property readOnlyNodeContacts keeps track of the most recent read-only nodes that contacted us, and how
 *      much they bother us.
 */
class NetworkNodes(
        val myself: PeerInfo,
        private val peerInfoMap: Map<NodeRid, PeerInfo>,
        private val readOnlyNodeContacts: MutableMap<NodeRid, Int>) {

    private var nextTimestamp: Long = 0 // Increases once a day

    companion object : KLogging() {
        const val MAX_DAILY_REQUESTS = 1000 // TODO: What to put here?
        const val DAY_IN_MILLIS = 24 * 60 * 60 * 1000

        fun buildNetworkNodes(peers: Collection<PeerInfo>, appConfig: AppConfig): NetworkNodes {
            if (peers.isEmpty()) {
                throw UserMistake("No peers have been configured for the network. Cannot proceed.")
            }
            val myKey: NodeRid = WrappedByteArray(appConfig.pubKeyByteArray)
            var me: PeerInfo? = null
            val peerMap = mutableMapOf<NodeRid, PeerInfo>()
            for (peer in peers) {
                val peerId = peer.peerId()
                if (peerId == myKey) {
                    val port = if (appConfig.hasPort) appConfig.port else peer.port
                    me = PeerInfo(peer.host, port, myKey.data)
                } else {
                    peerMap[peerId] = peer
                }
            }
            if (me == null) {
                throw UserMistake("We didn't find our peer ID (${myKey.toHex()}) in the list of given peers. Check the configuration for the node.")
            } else {
                return NetworkNodes(me, peerMap.toMap(), mutableMapOf())
            }
        }

        // Only for testing
        fun buildNetworkNodesDummy(): NetworkNodes {
            return NetworkNodes(PeerInfo("abc", 1, byteArrayOf(1)), mapOf(), mutableMapOf())
        }
    }

    fun hasPeers(): Boolean {
        return !peerInfoMap.isEmpty()
    }

    operator fun get(key: NodeRid): PeerInfo? = peerInfoMap[key]
    operator fun get(key: ByteArray): PeerInfo? = peerInfoMap[WrappedByteArray(key)]

    fun getPeerIds(): Set<NodeRid> {
        return peerInfoMap.keys
    }

    /**
     * Call this method before serving a read-only node
     *
     * @return true if the node is not bothering us too much.
     */
    @Synchronized
    fun isNodeBehavingWell(peerId: NodeRid, now: Long): Boolean {

        if (now > nextTimestamp) {
            val totalCalls = readOnlyNodeContacts.values.sum()
            logger.info("Clearing the read-only node overuse counter. " +
                    "Number of read-only nodes in contact since yesterday: ${readOnlyNodeContacts.size}. " +
                    "Total calls from read-only nodes: $totalCalls")
            nextTimestamp = now + DAY_IN_MILLIS
            readOnlyNodeContacts.clear()
        }

        val foundSigner = peerInfoMap[peerId]
        if (foundSigner != null) {
            // Do nothing
            // TODO: Only read-only nodes can be shut out currently (don't know what the limit should be for signers)
        } else {
            val foundHits = readOnlyNodeContacts[peerId]
            if (foundHits == null) {
                // Add it
                readOnlyNodeContacts[peerId] = 1
            } else {
                readOnlyNodeContacts[peerId] = foundHits + 1
                if (foundHits > MAX_DAILY_REQUESTS) {
                    if (foundHits == MAX_DAILY_REQUESTS + 1) {
                        logger.debug("Blocking read-only node with ID: ${peerId.toHex()} for a day ")
                    }
                    return false
                }
            }
        }

        return true
    }
}
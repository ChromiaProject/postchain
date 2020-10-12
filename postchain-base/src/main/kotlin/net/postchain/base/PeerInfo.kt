// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.core.ByteArrayKey
import net.postchain.network.x.XPeerID
import java.time.Instant
import java.util.concurrent.CountDownLatch

// TODO: Will be replaced by XPeerId
typealias PeerID = ByteArray


/**
 * A "peer" is either
 * 1. a block producer/signer who takes part in the consensus discussion.
 * 2. a read-only node
 */
open class PeerInfo(val host: String, open val port: Int, val pubKey: ByteArray, val timestamp: Instant? = null) {

    constructor(host: String, port: Int, pubKey: ByteArrayKey, timestamp: Instant? = null) : this(host, port, pubKey.byteArray, timestamp)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PeerInfo

        if (host != other.host) return false
        if (port != other.port) return false
        if (!pubKey.contentEquals(other.pubKey)) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = host.hashCode()
        result = 31 * result + port
        result = 31 * result + pubKey.contentHashCode()
        result = 31 * result + (timestamp?.hashCode() ?: 0)
        return result
    }
}

/**
 * This is a class used by integration tests to assign servers random ports. When this class is used to represent
 * a Peer, its server is started using port 0, which means that the operating system should assign a random
 * available port to the server to listen on. When the server has been assigned a port,
 * DynamicPortPeerInfo.portAssigned() must be called with the newly allocated port number.
 *
 * Calls to getPort() will block until portAssigned() has been called.
 */
open class DynamicPortPeerInfo(host: String, pubKey: ByteArray): PeerInfo(host, 0, pubKey) {
    private val latch = CountDownLatch(1)
    private var assignedPortNumber = 0

    override val port: Int get() {
        latch.await()
        return assignedPortNumber
    }

    fun portAssigned(port: Int) {
        assignedPortNumber = port
        latch.countDown()
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DynamicPortPeerInfo

        if (host != other.host) return false
        if (!pubKey.contentEquals(other.pubKey)) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = host.hashCode()
        result = 31 * result + 0
        result = 31 * result + pubKey.contentHashCode()
        result = 31 * result + (timestamp?.hashCode() ?: 0)
        return result
    }
}

/**
 * Returns [XPeerID] for given [PeerInfo.pubKey] object
 */
fun PeerInfo.peerId() = ByteArrayKey(this.pubKey)


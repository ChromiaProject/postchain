// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.common.data.ByteArrayKey
import net.postchain.common.toHex
import net.postchain.core.NodeRid
import java.time.Instant

// TODO: Will be replaced by NodeRid
typealias PeerID = ByteArray


/**
 * TODO: Olle: Should prob be renamed to NodeInfo now (since it's used by replicas, masters and sub-nodes alike)
 *
 * A "peer" is a Postchain node, either
 * 1. a block producer/signer who takes part in the consensus discussion.
 * 2. a read-only node (=replica)
 */
open class PeerInfo(val host: String, val port: Int, val pubKey: ByteArray, val timestamp: Instant? = null) {

    constructor(host: String, port: Int, pubKey: ByteArrayKey, timestamp: Instant? = null) : this(host, port, pubKey.byteArray, timestamp)

    fun getNodeRid() = NodeRid(pubKey)

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

    override fun toString(): String {
        return "PeerInfo(host='$host', port=$port, pubKey=${pubKey.toHex()})"
    }
}

/**
 * Returns [NodeRid] for given [PeerInfo.pubKey] object
 */
fun PeerInfo.peerId() = NodeRid(this.pubKey)


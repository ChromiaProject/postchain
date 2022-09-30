// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.common.toHex
import net.postchain.core.NodeRid
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.Nullable
import java.time.Instant
import java.time.Instant.ofEpochMilli

// TODO: Will be replaced by NodeRid
typealias PeerID = ByteArray


/**
 * TODO: Olle: Should prob be renamed to NodeInfo now (since it's used by replicas, masters and sub-nodes alike)
 *
 * A "peer" is a Postchain node, either
 * 1. a block producer/signer who takes part in the consensus discussion.
 * 2. a read-only node (=replica)
 */
class PeerInfo(
        @Name("host")
        val host: String,
        // Annotations added but ObjectMapper is not applicable b/c it can't convert Long to Int
        @Name("port")
        val port: Int,
        @Name("pubkey")
        val pubKey: ByteArray,
        // Annotations added but ObjectMapper is not applicable b/c default value for Instant
        @Name("last_updated")
        @Nullable
        val timestamp: Instant? = null
) {

    companion object {
        @JvmStatic
        fun fromGtv(gtv: Gtv): PeerInfo {
            return when (gtv) {
                is GtvArray -> {
                    val gtv0 = gtv.asArray()

                    if (gtv0.size < 3 || gtv0.size > 4) {
                        throw IllegalArgumentException("Can't create PeerInfo object from gtv: $gtv")
                    }

                    val lastUpdated = if (gtv0.size == 4) ofEpochMilli(gtv0[3].asInteger()) else null

                    PeerInfo(gtv0[0].asString(), gtv0[1].asInteger().toInt(), gtv0[2].asByteArray(), lastUpdated)
                }

                is GtvDictionary -> {
                    // Have tried to do gtv.toObject(emptyMap())
                    // But ObjectMapper can't convert Long to Int, etc.

                    val gtv0 = gtv.asDict()

                    val host = gtv0["host"]?.asString()
                    val port = gtv0["port"]?.asInteger()?.toInt()
                    val pubkey = gtv0["pubkey"]?.asByteArray()
                    val lastUpdated = gtv0["last_updated"]?.asInteger()?.let { ofEpochMilli(it) }

                    if (host == null || port == null || pubkey == null) {
                        throw IllegalArgumentException("Can't create PeerInfo object from gtv: $gtv")
                    }

                    PeerInfo(host, port, pubkey, lastUpdated)
                }

                else -> throw IllegalArgumentException("Can't create PeerInfo object from gtv: $gtv")
            }
        }
    }

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


// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.x

import net.postchain.base.BlockchainRid
import net.postchain.base.peerId
import net.postchain.core.byteArrayKeyOf
import net.postchain.debug.BlockchainProcessName
import net.postchain.network.IdentPacketInfo

enum class Direction {
    INCOMING, OUTGOING
}

class XPeerConnectionDescriptor(
        /**
         * It is *target* peer for client (outgoing) connections
         * and *source* peer for server (incoming) connections.
         */
        val peerId: XPeerID,
        val blockchainRid: BlockchainRid,
        val sessionKey: ByteArray? = null,
        val dir: Direction = Direction.OUTGOING
) {

    companion object Factory {

        fun createFromIdentPacketInfo(identPacketInfo: IdentPacketInfo): XPeerConnectionDescriptor {
            return XPeerConnectionDescriptor(
                    identPacketInfo.peerId.byteArrayKeyOf(),
                    identPacketInfo.blockchainRid, identPacketInfo.sessionKey, Direction.INCOMING)
        }

    }

    fun isOutgoing(): Boolean {
        return dir == Direction.OUTGOING
    }

    /**
     * Returns a convenient string of the format "[03B2:94]/[00:03]" to put into the logs.
     *                                            (Node id) (BC RID)
     */
    fun loggingPrefix(peerId: XPeerID): String = BlockchainProcessName(
            peerId.toString(),
            blockchainRid
    ).toString()
}

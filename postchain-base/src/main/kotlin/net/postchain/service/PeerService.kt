package net.postchain.service

import net.postchain.PostchainContext
import net.postchain.base.PeerInfo
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withWriteConnection
import net.postchain.common.exception.AlreadyExists
import net.postchain.crypto.PubKey

class PeerService(private val postchainContext: PostchainContext) {

    fun addPeer(pubkey: PubKey, host: String, port: Int, override: Boolean) {
        postchainContext.storage.withWriteConnection { ctx ->
            val db = DatabaseAccess.of(ctx)
            val targetHost = db.findPeerInfo(ctx, host, port, null)
            if (targetHost.isNotEmpty()) {
                throw AlreadyExists("Peer already exists on current host")
            }
            val targetKey = db.findPeerInfo(ctx, null, null, pubkey.hex())
            if (targetKey.isNotEmpty()) {
                if (!override) throw AlreadyExists("public key already added for a host, use override to add anyway")
                db.updatePeerInfo(ctx, host, port, pubkey.hex())

            } else {
                db.addPeerInfo(ctx, host, port, pubkey.hex())
            }
        }
    }

    fun removePeer(pubkey: PubKey): Array<PeerInfo> {
        return postchainContext.storage.withWriteConnection { ctx ->
            DatabaseAccess.of(ctx).removePeerInfo(ctx, pubkey.hex())
        }
    }

    fun listPeers(): Array<PeerInfo> {
        return postchainContext.storage.withWriteConnection { ctx ->
            DatabaseAccess.of(ctx).findPeerInfo(ctx, null, null, null)
        }
    }
}

package net.postchain.server.service

import net.postchain.PostchainContext
import net.postchain.base.PeerInfo
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withWriteConnection
import net.postchain.server.AlreadyExists

class PeerService(private val postchainContext: PostchainContext) {

    fun addPeer(pubkey: String, host: String, port: Int, override: Boolean) {
        postchainContext.storage.withWriteConnection { ctx ->
            val db = DatabaseAccess.of(ctx)
            val targetHost = db.findPeerInfo(ctx, host, port, null)
            if (targetHost.isNotEmpty()) {
                throw AlreadyExists("Peer already exists on current host")
            }
            val targetKey = db.findPeerInfo(ctx, null, null, pubkey)
            if (targetKey.isNotEmpty() && !override) {
                throw AlreadyExists("public key already added for a host, use to add anyway")
            }
            db.addPeerInfo(ctx, host, port, pubkey)
        }
    }

    fun removePeer(pubkey: String): Array<PeerInfo> {
        return postchainContext.storage.withWriteConnection { ctx ->
            DatabaseAccess.of(ctx).removePeerInfo(ctx, pubkey)
        }
    }

    fun listPeers(): Array<PeerInfo> {
        return postchainContext.storage.withWriteConnection { ctx ->
            DatabaseAccess.of(ctx).findPeerInfo(ctx, null, null, null)
        }
    }
}

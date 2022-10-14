package net.postchain.service

import net.postchain.PostchainContext
import net.postchain.api.internal.PostchainApi
import net.postchain.base.PeerInfo
import net.postchain.base.withWriteConnection
import net.postchain.crypto.PubKey

class PeerService(private val postchainContext: PostchainContext) {

    fun addPeer(pubkey: PubKey, host: String, port: Int, override: Boolean): Boolean =
            postchainContext.storage.withWriteConnection { ctx ->
                PostchainApi.addPeer(ctx, pubkey, host, port, override)
            }


    fun removePeer(pubkey: PubKey): Array<PeerInfo> = postchainContext.storage.withWriteConnection { ctx ->
        PostchainApi.removePeer(ctx, pubkey)
    }

    fun listPeers(): Array<PeerInfo> = postchainContext.storage.withWriteConnection { ctx ->
        PostchainApi.listPeers(ctx)
    }
}

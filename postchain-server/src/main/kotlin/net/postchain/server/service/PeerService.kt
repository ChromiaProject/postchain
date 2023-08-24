package net.postchain.server.service

import net.postchain.api.internal.PeerApi
import net.postchain.base.PeerInfo
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.core.Storage
import net.postchain.crypto.PubKey
import net.postchain.server.NodeProvider

class PeerService(private val nodeProvider: NodeProvider) {

    val storage: Storage get() = nodeProvider.get().postchainContext.sharedStorage

    fun addPeer(pubkey: PubKey, host: String, port: Int, override: Boolean): Boolean =
            storage.withWriteConnection { ctx ->
                PeerApi.addPeer(ctx, pubkey, host, port, override)
            }

    fun removePeer(pubkey: PubKey): Array<PeerInfo> = storage.withWriteConnection { ctx ->
        PeerApi.removePeer(ctx, pubkey)
    }

    fun listPeers(): Array<PeerInfo> = storage.withReadConnection { ctx ->
        PeerApi.listPeers(ctx)
    }
}
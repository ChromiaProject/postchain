package net.postchain.api.internal

import net.postchain.base.PeerInfo
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.exception.AlreadyExists
import net.postchain.common.toHex
import net.postchain.core.AppContext
import net.postchain.crypto.PubKey

object PeerApi {

    fun addPeer(ctx: AppContext, pubkey: PubKey, host: String, port: Int, override: Boolean): Boolean {
        val db = DatabaseAccess.of(ctx)
        val targetHost = db.findPeerInfo(ctx, host, port, null)
        if (targetHost.isNotEmpty()) {
            throw AlreadyExists("Peer already exists on current host with pubkey ${targetHost[0].pubKey.toHex()}")
        }
        val targetKey = db.findPeerInfo(ctx, null, null, pubkey.hex())
        return if (targetKey.isNotEmpty()) {
            if (override) {
                db.updatePeerInfo(ctx, host, port, pubkey)
            } else {
                false
            }
        } else {
            db.addPeerInfo(ctx, host, port, pubkey.hex())
        }
    }

    fun addPeers(ctx: AppContext, peerInfos: Collection<PeerInfo>): Array<PeerInfo> {
        val db = DatabaseAccess.of(ctx)

        val imported = mutableListOf<PeerInfo>()
        peerInfos.forEach { peerInfo ->
            val noHostPort = db.findPeerInfo(ctx, peerInfo.host, peerInfo.port, null).isEmpty()
            val noPubKey = db.findPeerInfo(ctx, null, null, peerInfo.pubKey.toHex()).isEmpty()

            if (noHostPort && noPubKey) {
                val added = db.addPeerInfo(
                        ctx, peerInfo.host, peerInfo.port, peerInfo.pubKey.toHex(), peerInfo.lastUpdated)

                if (added) {
                    imported.add(peerInfo)
                }
            }
        }
        return imported.toTypedArray()
    }

    fun removePeer(ctx: AppContext, pubkey: PubKey): Array<PeerInfo> =
            DatabaseAccess.of(ctx).removePeerInfo(ctx, pubkey)

    fun listPeers(ctx: AppContext): Array<PeerInfo> =
            DatabaseAccess.of(ctx).findPeerInfo(ctx, null, null, null)

    fun findPeerInfo(ctx: AppContext, host: String?, port: Int?, pubKeyPattern: String?) =
            DatabaseAccess.of(ctx).findPeerInfo(ctx, host, port, pubKeyPattern)
}

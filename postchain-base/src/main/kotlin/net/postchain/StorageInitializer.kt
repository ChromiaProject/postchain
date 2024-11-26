package net.postchain

import net.postchain.base.data.DatabaseAccess
import net.postchain.common.toHex
import net.postchain.config.app.AppConfig
import net.postchain.core.AppContext

object StorageInitializer {

    fun setupInitialPeers(appConfig: AppConfig, ctx: AppContext) {
        val hasOwnPeer =
                DatabaseAccess.of(ctx).findPeerInfo(ctx, null, null, appConfig.pubKey).isNotEmpty()
        if (!hasOwnPeer) {
            DatabaseAccess.of(ctx).addPeerInfo(ctx, "localhost", appConfig.port, appConfig.pubKey)
        }
        if (appConfig.initialPeer != null) {
            addInitialPeer(ctx, appConfig)
        }
    }

    private fun addInitialPeer(it: AppContext, appConfig: AppConfig) {
        val initialPeer = appConfig.initialPeer
        if (initialPeer != null) {
            if (initialPeer.pubKey.contentEquals(appConfig.pubKeyByteArray)) return
            val hasInitialPeer =
                    DatabaseAccess.of(it).findPeerInfo(it, null, null, initialPeer.pubKey.toHex()).isNotEmpty()

            if (!hasInitialPeer) {
                DatabaseAccess.of(it).addPeerInfo(it,
                        initialPeer.host,
                        initialPeer.port,
                        initialPeer.pubKey.toHex()
                )
            }
        }
    }
}

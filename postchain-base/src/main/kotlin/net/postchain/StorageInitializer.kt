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
        if (appConfig.genesisPeer != null) {
            addGenesisPeer(ctx, appConfig)
        }
    }

    private fun addGenesisPeer(it: AppContext, appConfig: AppConfig) {
        val genesisPeer = appConfig.genesisPeer
        if (genesisPeer != null) {
            if (genesisPeer.pubKey.contentEquals(appConfig.pubKeyByteArray)) return
            val hasGenesisPeer =
                    DatabaseAccess.of(it).findPeerInfo(it, null, null, genesisPeer.pubKey.toHex()).isNotEmpty()

            if (!hasGenesisPeer) {
                DatabaseAccess.of(it).addPeerInfo(it,
                        genesisPeer.host,
                        genesisPeer.port,
                        genesisPeer.pubKey.toHex()
                )
            }
        }
    }
}

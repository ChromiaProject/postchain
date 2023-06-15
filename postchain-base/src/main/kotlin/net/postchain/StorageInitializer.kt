package net.postchain

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withWriteConnection
import net.postchain.common.toHex
import net.postchain.config.app.AppConfig
import net.postchain.core.AppContext
import net.postchain.core.Storage

object StorageInitializer {

    fun setupInitialPeers(appConfig: AppConfig, storage: Storage) {
        storage.withWriteConnection {
            val hasOwnPeer =
                    DatabaseAccess.of(it).findPeerInfo(it, null, null, appConfig.pubKey).isNotEmpty()
            if (!hasOwnPeer) {
                DatabaseAccess.of(it).addPeerInfo(it, "localhost", appConfig.port, appConfig.pubKey)
            }
            if (appConfig.genesisPeer != null) {
                addGenesisPeer(it, appConfig)
            }
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

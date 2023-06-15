package net.postchain

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.config.app.AppConfig
import net.postchain.core.Storage

object StorageInitializer {

    fun setupInitialPeers(appConfig: AppConfig, storage: Storage) {
        val hasOwnPeer = storage.withReadConnection {
            DatabaseAccess.of(it).findPeerInfo(it, null, null, appConfig.pubKey).isNotEmpty()
        }
        if (!hasOwnPeer) {
            storage.withWriteConnection {
                DatabaseAccess.of(it).addPeerInfo(it, "localhost", appConfig.port, appConfig.pubKey)
            }
        }
        val genesisPubkey = appConfig.getEnvOrString("POSTCHAIN_GENESIS_PUBKEY", "genesis.pubkey")
        if (genesisPubkey != null) {
            require(appConfig.hasEnvOrKey("POSTCHAIN_GENESIS_HOST", "genesis.host")) { "Node configuration must contain genesis.host if genesis.pubkey is supplied" }
            require(appConfig.hasEnvOrKey("POSTCHAIN_GENESIS_PORT", "genesis.port")) { "Node configuration must contain genesis.port if genesis.pubkey if supplied" }
            val hasGenesisPeer = storage.withReadConnection {
                DatabaseAccess.of(it).findPeerInfo(it, null, null, genesisPubkey).isNotEmpty()
            }
            if (!hasGenesisPeer) {
                storage.withReadConnection {
                    DatabaseAccess.of(it).addPeerInfo(it,
                            appConfig.getEnvOrString("POSTCHAIN_GENESIS_HOST", "genesis.host", ""),
                            appConfig.getEnvOrInt("POSTCHAIN_GENESIS_PORT", "genesis.port", 0),
                            genesisPubkey)
                }
            }
        }
    }
}
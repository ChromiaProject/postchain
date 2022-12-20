// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import net.postchain.base.PeerInfo
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.peerId
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.core.NodeRid
import net.postchain.core.Storage

open class ManualNodeConfigurationProvider(
        protected val appConfig: AppConfig,
        createStorage: (AppConfig) -> Storage
) : NodeConfigurationProvider {

    protected val storage = createStorage(appConfig).also { setupMyself(it) }

    private fun setupMyself(storage: Storage) {
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

    override fun getConfiguration(): NodeConfig {
        return object : NodeConfig(appConfig) {
            override val peerInfoMap = getPeerInfoCollection(appConfig).associateBy(PeerInfo::peerId)
            override val blockchainReplicaNodes = getBlockchainReplicaCollection(appConfig)
            override val mustSyncUntilHeight = getSyncUntilHeight(appConfig)
        }
    }

    override fun close() {
        storage.close()
    }

    /**
     *
     *
     * @param appConfig is the
     * @return the [PeerInfo] this node should know about
     */
    // TODO: [et]: Make it protected
    open fun getPeerInfoCollection(appConfig: AppConfig): Array<PeerInfo> {
        return storage.withReadConnection { ctx ->
            DatabaseAccess.of(ctx).getPeerInfoCollection(ctx)
        }
    }

    open fun getBlockchainReplicaCollection(appConfig: AppConfig): Map<BlockchainRid, List<NodeRid>> {
        return storage.withReadConnection { ctx ->
            DatabaseAccess.of(ctx).getBlockchainReplicaCollection(ctx)
        }
    }

    open fun getSyncUntilHeight(appConfig: AppConfig): Map<Long, Long> {
        return storage.withReadConnection { ctx ->
            DatabaseAccess.of(ctx).getMustSyncUntil(ctx)
        }
    }

    open fun getChainIDs(appConfig: AppConfig): Map<BlockchainRid, Long> {
        return storage.withReadConnection { ctx ->
            DatabaseAccess.of(ctx).getChainIds(ctx)
        }
    }
}
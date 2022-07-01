// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import net.postchain.base.PeerInfo
import net.postchain.base.Storage
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.peerId
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.core.NodeRid

/**
 *
 */
open class ManualNodeConfigurationProvider(
        protected val appConfig: AppConfig,
        createStorage: (AppConfig) -> Storage
) : NodeConfigurationProvider {

    private val storage = createStorage(appConfig).also { setupMyself(it) }

    private fun setupMyself(storage: Storage) {
        val hasOwnPeer = storage.withReadConnection {
            DatabaseAccess.of(it).findPeerInfo(it, null, null, appConfig.pubKey).isNotEmpty()
        }
        if (!hasOwnPeer) {
            storage.withWriteConnection {
                DatabaseAccess.of(it).addPeerInfo(it, "localhost", appConfig.port, appConfig.pubKey)
            }
        }
    }

    override fun getConfiguration(): NodeConfig {
        return object : NodeConfig(appConfig) {
            override val peerInfoMap = getPeerInfoCollection(appConfig)
                    .associateBy(PeerInfo::peerId)
            override val blockchainReplicaNodes = getBlockchainReplicaCollection(appConfig)
            override val blockchainsToReplicate: Set<BlockchainRid> = getBlockchainsToReplicate(appConfig)
            override val mustSyncUntilHeight: Map<Long, Long> = getSyncUntilHeight(appConfig)
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

    open fun getBlockchainsToReplicate(appConfig: AppConfig): Set<BlockchainRid> {
        return storage.withReadConnection {
            ctx -> DatabaseAccess.of(ctx).getBlockchainsToReplicate(ctx, appConfig.pubKey)
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
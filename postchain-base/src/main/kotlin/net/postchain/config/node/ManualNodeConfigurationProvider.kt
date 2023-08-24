// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import net.postchain.base.PeerInfo
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.peerId
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.core.NodeRid
import net.postchain.core.Storage

open class ManualNodeConfigurationProvider(
        protected val appConfig: AppConfig,
        protected val storage: Storage,
) : NodeConfigurationProvider {

    override fun getConfiguration(): NodeConfig {
        val blockchainReplicaCollection = getBlockchainReplicaCollection()
        return object : NodeConfig(appConfig) {
            override val peerInfoMap = getPeerInfoCollection().associateBy(PeerInfo::peerId)
            override val blockchainReplicaNodes = blockchainReplicaCollection
            override val locallyConfiguredBlockchainReplicaNodes = blockchainReplicaCollection
            override val mustSyncUntilHeight = getSyncUntilHeight()
        }
    }

    override fun close() {
        storage.close()
    }

    open fun getPeerInfoCollection(): Array<PeerInfo> {
        return storage.withReadConnection { ctx ->
            DatabaseAccess.of(ctx).getPeerInfoCollection(ctx)
        }
    }

    open fun getBlockchainReplicaCollection(): Map<BlockchainRid, List<NodeRid>> {
        return storage.withReadConnection { ctx ->
            DatabaseAccess.of(ctx).getBlockchainReplicaCollection(ctx)
        }
    }

    open fun getSyncUntilHeight(): Map<Long, Long> {
        return storage.withReadConnection { ctx ->
            DatabaseAccess.of(ctx).getMustSyncUntil(ctx)
        }
    }

    open fun getChainIDs(): Map<BlockchainRid, Long> {
        return storage.withReadConnection { ctx ->
            DatabaseAccess.of(ctx).getChainIds(ctx)
        }
    }
}
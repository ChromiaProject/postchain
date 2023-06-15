// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import net.postchain.base.PeerInfo
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.peerId
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.types.WrappedByteArray
import net.postchain.config.app.AppConfig
import net.postchain.core.NodeRid
import net.postchain.core.Storage
import java.time.Instant.EPOCH

class ManagedNodeConfigurationProvider(
        appConfig: AppConfig,
        storage: Storage
) : ManualNodeConfigurationProvider(
        appConfig,
        storage
) {

    private var managedPeerSource: PeerInfoDataSource? = null

    fun setPeerInfoDataSource(peerInfoDataSource: PeerInfoDataSource?) {
        managedPeerSource = peerInfoDataSource
    }

    override fun getConfiguration(): NodeConfig {
        return object : ManagedNodeConfig(appConfig) {
            override val peerInfoMap = getPeerInfoCollection().associateBy(PeerInfo::peerId)
            override val blockchainReplicaNodes = getBlockchainReplicaCollection()
            override val locallyConfiguredBlockchainsToReplicate = getLocallyConfiguredBlockchainsToReplicate(appConfig)
            override val mustSyncUntilHeight = getSyncUntilHeight()
        }
    }

    /**
     * This will collect PeerInfos from two sources:
     *
     * 1. The local peerinfos table (common for all blockchains)
     * 2. The chain0 c0.node table
     *
     * If there are multiple peerInfos for a specific key, the peerInfo
     * with highest timestamp takes presedence. A null timestamp is considered
     * older than a non-null timestamp.
     *
     * The timestamp is taken directly from the respective table, c0.node_list
     * is not involved here.
     */
    override fun getPeerInfoCollection(): Array<PeerInfo> {
        val peerInfoMap = mutableMapOf<WrappedByteArray, PeerInfo>()

        // Define pick function
        val peerInfoPicker: (PeerInfo) -> Unit = { peerInfo ->
            peerInfoMap.merge(peerInfo.peerId(), peerInfo) { old, new ->
                if (old.lastUpdated ?: EPOCH < new.lastUpdated ?: EPOCH) new else old
            }
        }

        // Collect peerInfos from local peerinfos table (common for all bcs)
        super.getPeerInfoCollection().forEach(peerInfoPicker)
        // get the peerInfos from the chain0.node table
        managedPeerSource?.getPeerInfos()?.forEach(peerInfoPicker)

        return peerInfoMap.values.toTypedArray()
    }

    /**
     * This will collect BlockchainReplicas from two sources:
     *
     * 1. The local blockchain_replicas table (common for all blockchains)
     * 2. The chain0 c0.blockchain_replica_node table
     *
     */
    override fun getBlockchainReplicaCollection(): Map<BlockchainRid, List<NodeRid>> {
        // Collect from local table (common for all bcs)
        val localResMap = super.getBlockchainReplicaCollection()
        // get values from the chain0 table
        val chain0ResMap = managedPeerSource?.getBlockchainReplicaNodeMap() ?: mapOf()

        val resMap = mutableMapOf<BlockchainRid, List<NodeRid>>()
        val allKeys = localResMap.keys + chain0ResMap.keys
        for (k in allKeys) {
            resMap[k] = merge(localResMap[k], chain0ResMap[k])
        }
        return resMap
    }

    fun merge(a: List<NodeRid>?, b: List<NodeRid>?): List<NodeRid> {
        if (a == null) {
            return b!!
        }
        if (b == null) {
            return a
        }
        return a.toSet().union(b).toList()
    }

    override fun getSyncUntilHeight(): Map<Long, Long> {
        //collect from local table: mapOf<chainID,height>
        val localMap = super.getSyncUntilHeight()

        //collect from chain0 table. Mapped to brid instead of chainID, since chainID does not exist here. It is local.
        val bridToHeightMap = managedPeerSource?.getSyncUntilHeight() ?: mapOf()

        //brid2Height => chainID2height
        val bridToChainID = super.getChainIDs()
        val c0Heights = mutableMapOf<Long, Long>()
        for (x in bridToHeightMap) {
            val chainIdKey = bridToChainID[x.key]
            // We may be computing this when chain has been added to chain0 table but not yet to "blockchains" table
            // this should not result in a NPE, ignore setting height until we have the chain in both tables
            if (chainIdKey != null) {
                c0Heights[chainIdKey] = x.value
            }
        }

        // Primary source of height information is from local table, if not found there, use values from c0 tables.
        val resMap = mutableMapOf<Long, Long>()
        val allKeys = localMap.keys + c0Heights.keys
        for (k in allKeys) {
            resMap[k] = mergeLong(localMap[k], c0Heights[k])
        }
        return resMap
    }

    protected fun mergeLong(a: Long?, b: Long?): Long {
        if (a == null) {
            return b!!
        }
        if (b == null) {
            return a
        }
        return maxOf(a, b)
    }

    fun getLocallyConfiguredBlockchainsToReplicate(appConfig: AppConfig): Set<BlockchainRid> {
        return storage.withReadConnection { ctx ->
            DatabaseAccess.of(ctx).getBlockchainsToReplicate(ctx, appConfig.pubKey)
        }
    }
}
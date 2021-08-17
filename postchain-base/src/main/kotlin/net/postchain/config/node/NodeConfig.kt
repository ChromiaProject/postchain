// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import net.postchain.base.BlockchainRid
import net.postchain.base.PeerInfo
import net.postchain.common.hexStringToByteArray
import net.postchain.config.app.AppConfig
import net.postchain.core.Infrastructure
import net.postchain.network.x.XPeerID
import org.apache.commons.configuration2.Configuration

open class NodeConfig(val appConfig: AppConfig) {

    companion object {
        const val DEFAULT_PORT: Int = 9870
    }

    private val config: Configuration
        get() = appConfig.config

    @Deprecated("POS-129: Defined implicitly by 'infrastructure' param")
            /**
             * Blockchain configuration provider
             */
    val blockchainConfigProvider: String
        // manual | managed
        get() = config.getString("configuration.provider.blockchain", "")

    val infrastructure: String
        // "base/ebft" is the default
        get() = config.getString("infrastructure", Infrastructure.Ebft.get())

    /**
     * Container chains
     */
    val containerImage: String
        get() = config.getString("containerChains.dockerImage", "chromaway/postchain-subnode:latest")
    val subnodeRestApiPort: Int
        get() = config.getInt("containerChains.api.port", 7740)

    // Used by subnode to connect to master for inter-node communication.
    val masterHost: String
        get() = config.getString("containerChains.masterHost", "localhost")

    val masterPort: Int
        get() = config.getInt("containerChains.masterPort", 9860)

    // Used by master
    val slaveHost: String
        get() = config.getString("containerChains.slaveHost", "localhost")

    /**
     * Database
     */
    val databaseDriverclass: String
        get() = appConfig.databaseDriverclass

    val databaseUrl: String
        get() = appConfig.databaseUrl

    val databaseSchema: String
        get() = appConfig.databaseSchema

    val databaseUsername: String
        get() = appConfig.databaseUsername

    val databasePassword: String
        get() = appConfig.databasePassword


    /**
     * Heartbeat and RemoteConfig (for Subnode only)
     */
    // Enables/disables heartbeat check
    val heartbeatEnabled: Boolean
        get() = config.getBoolean("heartbeat.enabled", true)

    // Heartbeat check is failed if there is no heartbeat event registered for the last
    // `max(maxBlockTime, heartbeatTimeout)` ms
    val heartbeatTimeout: Long
        get() = config.getLong("heartbeat.timeout", 60_000L)

    // BlockchainProcess sleeps for `heartbeatSleepTimeout` ms after every failed Heartbeat check
    val heartbeatSleepTimeout: Long
        get() = config.getLong("heartbeat.sleep_timeout", 5_000L)

    // Enables/disables remote config check
    val remoteConfigEnabled: Boolean
        get() = config.getBoolean("remote_config.enabled", true)

    // Remote config is requested every `max(maxBlockTime, remoteConfigRequestInterval)` ms
    val remoteConfigRequestInterval: Long
        get() = config.getLong("remote_config.request_interval", 20_000L)

    // Remote config check is failed if there is no remote config response registered for the last
    // `max(maxBlockTime, remoteConfigTimeout)` ms
    val remoteConfigTimeout: Long
        get() = config.getLong("remote_config.request_timeout", 60_000L)


    /**
     * Pub/Priv keys
     */
    val privKey: String
        get() = config.getString("messaging.privkey", "")

    val privKeyByteArray: ByteArray
        get() = privKey.hexStringToByteArray()

    val pubKey: String
        get() = config.getString("messaging.pubkey", "")

    val pubKeyByteArray: ByteArray
        get() = pubKey.hexStringToByteArray()


    /**
     * REST API
     */
    val restApiBasePath: String
        get() = config.getString("api.basepath", "")

    val restApiPort: Int
        get() = config.getInt("api.port", 7740)

    val restApiSsl: Boolean
        get() = config.getBoolean("api.enable_ssl", false)

    val restApiSslCertificate: String
        get() = config.getString("api.ssl_certificate", "")

    val restApiSslCertificatePassword: String
        get() = config.getString("api.ssl_certificate.password", "")

    /**
     * Peers
     */
    open val peerInfoMap: Map<XPeerID, PeerInfo> = mapOf()

    // List of replicas for a given node
    open val nodeReplicas: Map<XPeerID, List<XPeerID>> = mapOf()
    open val blockchainReplicaNodes: Map<BlockchainRid, List<XPeerID>> = mapOf()
    open val blockchainsToReplicate: Set<BlockchainRid> = setOf()
    open val blockchainAncestors: Map<BlockchainRid, Map<BlockchainRid, Set<XPeerID>>> = getAncestors()

    open val mustSyncUntilHeight: Map<Long, Long>? = mapOf() //mapOf<chainID, height>

    // FastSync
    val fastSyncExitDelay: Long
        get() = config.getLong("fastsync.exit_delay", 60000)

    val fastSyncJobTimeout: Long
        get() = config.getLong("fastsync.job_timeout", 10000)

    private fun getAncestors(): Map<BlockchainRid, Map<BlockchainRid, Set<XPeerID>>> {
        val ancestors = config.subset("blockchain_ancestors") ?: return emptyMap()
        val forBrids = ancestors.getKeys()
        val result = mutableMapOf<BlockchainRid, MutableMap<BlockchainRid, MutableSet<XPeerID>>>()
        forBrids.forEach {
            val list = ancestors.getList(String::class.java, it)
            val map = LinkedHashMap<BlockchainRid, MutableSet<XPeerID>>()
            list.forEach {
                val peerAndBrid = it.split(":")
                val peer = XPeerID(peerAndBrid[0].hexStringToByteArray())
                val brid = BlockchainRid.buildFromHex(peerAndBrid[1])
                map.computeIfAbsent(brid) { mutableSetOf() }.add(peer)
            }
            result[BlockchainRid.buildFromHex(it)] = map
        }
        return result
    }

    /**
     * Active Chains
     *
     * Note: This is only needed for tests (asked Tykulov about it)
     * TODO: [et]: Resolve this issue ('activeChainIds')
     */
    val activeChainIds: Array<String>
        get() {
            return if (config.containsKey("activechainids"))
                config.getStringArray("activechainids")
            else
                emptyArray()
        }
}
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
    val subnodeRestApiPort: Int
        get() = config.getInt("containerChains.api.port", 7740)

//     Used by subnode to connect to master for inter-node communication.
    val masterHost: String
        get() = config.getString("containerChains.masterHost", "localhost")

    val masterPort: Int
        get() = config.getInt("containerChains.masterPort", 9860)

//     Used by master
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
     * Heartbeat
     */
    val heartbeat: Boolean
        get() = config.getBoolean("heartbeat.enabled", true)
    val heartbeatTimeout: Long
        get() = config.getLong("heartbeat.timeout", 60_000L)
    val heartbeatSleepTimeout: Long
        get() = config.getLong("heartbeat.sleep_timeout", 500L)


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
    open val nodeReplicas: Map<XPeerID, List<XPeerID>> = mapOf()
    open val blockchainReplicaNodes: Map<BlockchainRid, List<XPeerID>> = mapOf()
    open val blockchainAliases: Map<BlockchainRid, Map<BlockchainRid, Set<XPeerID>>> = getAliases()

    open val mustSyncUntilHeight: Map<Long, Long>? = mapOf() //mapOf<chainID, height>

    val fastSyncExitDelay: Long
        get() = config.getLong("fastsync.exit_delay", 60000)

    val fastSyncJobTimeout: Long
        get() = config.getLong("fastsync.job_timeout", 10000)

    private fun getAliases(): Map<BlockchainRid, Map<BlockchainRid, Set<XPeerID>>> {
        val aliases = config.subset("blockchain_aliases") ?: return emptyMap()
        val forBrids = aliases.getKeys()
        val result = mutableMapOf<BlockchainRid, MutableMap<BlockchainRid, MutableSet<XPeerID>>>()
        forBrids.forEach {
            val list = aliases.getList(String::class.java, it)
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
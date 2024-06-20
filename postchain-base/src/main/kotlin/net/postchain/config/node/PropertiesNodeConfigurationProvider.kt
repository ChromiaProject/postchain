// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import net.postchain.base.PeerInfo
import net.postchain.common.hexStringToByteArray
import net.postchain.config.app.AppConfig

class PropertiesNodeConfigurationProvider(private val appConfig: AppConfig) : NodeConfigurationProvider {

    private val configuration by lazy {
        object : NodeConfig(appConfig) {
            override val peerInfoMap = createPeerInfoCollection(appConfig).associateBy { it.getNodeRid() }
        }
    }

    override fun getConfiguration() = configuration

    override fun close() {}

    /**
     * Retrieves peer information from config, including networking info and public keys
     */
    private fun createPeerInfoCollection(config: AppConfig): Array<PeerInfo> {
        val nodeIndexes = config.getKeys("node").asSequence()
                .map { it.split(".")[1].toInt() }
                .toSortedSet()

        return nodeIndexes.map {
            val port = config.getInt("node.$it.port")
            val host = config.getString("node.$it.host")
            val pubKey = config.getString("node.$it.pubkey").hexStringToByteArray()
            PeerInfo(host, port, pubKey)
        }.toTypedArray()
    }
}
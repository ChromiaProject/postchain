package net.postchain.devtools.utils.configuration

import net.postchain.base.PeerInfo
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfig
import net.postchain.config.node.NodeConfigurationProvider
import org.apache.commons.configuration2.Configuration

class TestNodeConfigurationProvider(private val appConfig: AppConfig) : NodeConfigurationProvider {
    override fun getConfiguration(): NodeConfig {
        return object : NodeConfig(appConfig) {
            override val peerInfoMap = createPeerInfoCollection(appConfig.config).associateBy { it.getNodeRid() }
        }
    }

    override fun close() {}

    /**
     * Retrieves peer information from config, including networking info and public keys
     */
    private fun createPeerInfoCollection(config: Configuration): Array<PeerInfo> {
        val peerInfos = config.getProperty("testpeerinfos")
        if (peerInfos != null) {
            return if (peerInfos is PeerInfo) {
                arrayOf(peerInfos)
            } else {
                (peerInfos as List<PeerInfo>).toTypedArray()
            }
        }
        return emptyArray()
    }
}
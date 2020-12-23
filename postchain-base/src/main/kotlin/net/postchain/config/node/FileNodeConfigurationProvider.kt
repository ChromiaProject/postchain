package net.postchain.config.node

import net.postchain.base.PeerInfo
import net.postchain.base.peerId
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.config.app.AppConfig
import org.apache.commons.configuration2.Configuration

class FileNodeConfigurationProvider(private val appConfig: AppConfig) : NodeConfigurationProvider {

    companion object {

        fun packPeerInfo(peerInfo: PeerInfo): String {
            return "${peerInfo.host};${peerInfo.port};${peerInfo.pubKey.toHex()}"
        }

        fun packPeerInfoCollection(peerInfos: Collection<PeerInfo>): List<String> {
            return peerInfos.map { packPeerInfo(it) }
        }

        fun unpackPeerInfo(str: String): PeerInfo {
            val tokens = str.split(";")
            return PeerInfo(tokens[0], tokens[1].toInt(), tokens[2].hexStringToByteArray())
        }
    }

    override fun getConfiguration(): NodeConfig {
        return object : NodeConfig(appConfig) {
            override val peerInfoMap = getPeerInfoCollection(appConfig.config)
                    .associateBy(PeerInfo::peerId)
        }
    }

    private fun getPeerInfoCollection(config: Configuration): Array<PeerInfo> {
        val peerinfos = config.getStringArray("peerinfos").map {
            it.replace("[", "").replace("]", "")
        }
        return peerinfos.map { pi -> unpackPeerInfo(pi) }.toTypedArray()
    }

    override fun close() {}
}
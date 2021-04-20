package net.postchain.managed

import net.postchain.base.BlockchainRid
import net.postchain.config.node.NodeConfig
import net.postchain.core.BlockQueries
import net.postchain.gtv.GtvFactory

class BaseDirectoryDataSource(queries: BlockQueries, nodeConfig: NodeConfig) : BaseManagedNodeDataSource(queries, nodeConfig), DirectoryDataSource {


    override fun getContainersToRun(): List<String>? {
        val res = queries.query("nm_get_containers",
                buildArgs("pubkey" to GtvFactory.gtv(nodeConfig.pubKeyByteArray))
        ).get()

        return res.asArray().map { it.asString() }
    }

    override fun getBlockchainsForContainer(containerID: String): List<BlockchainRid>? {
        val res = queries.query(
                "nm_get_blockchains_for_container",
                buildArgs("container_id" to GtvFactory.gtv(containerID))
        ).get()

        return res.asArray().map { BlockchainRid(it.asByteArray()) }
    }

    override fun getResourceLimitForContainer(containerID: String): Map<String, Long>? {
        val queryReply = queries.query(
                "nm_get_container_limits",
                buildArgs("container_id" to GtvFactory.gtv(containerID))
        ).get().asDict()
        val resList = queryReply.map { it.key to it.value.asInteger() }.toMap()
        return resList
    }


//    override fun getPeerListVersion(): Long {
//        return 0L
//    }
//
//    override fun computeBlockchainList(): List<ByteArray> {
//        return emptyList()
//    }
//
//    override fun getConfiguration(blockchainRIDRaw: ByteArray, height: Long): ByteArray? {
//        return null
//    }
//
//    override fun findNextConfigurationHeight(blockchainRIDRaw: ByteArray, height: Long): Long? {
//        return null
//    }
//
//    override fun getPeerInfos(): Array<PeerInfo> {
//        return arrayOf(PeerInfo("127.0.0.1", 9900, "AAAA".hexStringToByteArray(), Instant.EPOCH))
//    }
//
//    override fun getNodeReplicaMap(): Map<XPeerID, List<XPeerID>> {
//        return mapOf(XPeerID(byteArrayOf(0)) to emptyList())
//    }
//
//    override fun getBlockchainReplicaNodeMap(): Map<BlockchainRid, List<XPeerID>> {
//        return mapOf(BlockchainRid.EMPTY_RID to emptyList())
//    }
}

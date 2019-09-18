package net.postchain.integrationtest.managedmode

import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.core.EContext
import net.postchain.gtv.*
import net.postchain.gtx.SimpleGTXModule
import net.postchain.integrationtest.managedmode.PeerInfos.Companion.peerInfo0
import net.postchain.integrationtest.managedmode.PeerInfos.Companion.peerInfo1

open class ManagedTestModule(node: Nodes) : SimpleGTXModule<ManagedTestModule.Companion.Nodes>(
        node,
        mapOf(),
        mapOf(
                "nm_get_peer_infos" to ::queryGetPeerInfos,
                "nm_get_peer_list_version" to ::queryGetPeerListVersion,
                "nm_compute_blockchain_list" to ::queryComputeBlockchainList,
                "nm_get_blockchain_configuration" to ::queryGetConfiguration,
                "nm_find_next_configuration_height" to ::queryFindNextConfigurationHeight
        )
) {

    override fun initializeDB(ctx: EContext) {}

    companion object : KLogging() {

        enum class Nodes {
            Node0, Node1
        }

        private val stage0 = 0 until 15
        private val stage1 = 15 until 30

        fun queryGetPeerInfos(node: Nodes, eContext: EContext, args: Gtv): Gtv {
            logger.error { "Query: nm_get_peer_infos" }

            if (argCurrentHeight(args) in stage0)
                logger.error { "in range 0" }
            if (argCurrentHeight(args) in stage1)
                logger.error { "in range 1" }

            val peerInfos = when (argCurrentHeight(args)) {
                in stage0 -> {
                    when (node) {
                        Nodes.Node0 -> arrayOf(peerInfo0)
                        Nodes.Node1 -> arrayOf(peerInfo1)
                    }
                }
                in stage1 -> {
                    logger.error { "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@2" }
                    arrayOf(peerInfo0, peerInfo1)
                }
                else -> emptyArray()
            }

            return GtvArray(peerInfos
                    .map(::peerInfoToGtv)
                    .toTypedArray()
            )
        }

        fun queryGetPeerListVersion(node: Nodes, eContext: EContext, args: Gtv): Gtv {
            logger.error { "Query: nm_get_peer_list_version" }

            if (argCurrentHeight(args) in stage0)
                logger.error { "in range 0" }
            if (argCurrentHeight(args) in stage1)
                logger.error { "in range 1" }

            val version = when (argCurrentHeight(args)) {
                in stage0 -> 1
                in stage1 -> 2
                else -> 2
            }

            return GtvInteger(version.toLong())
        }

        fun queryComputeBlockchainList(node: Nodes, eContext: EContext, args: Gtv): Gtv {
            logger.error { "Query: nm_compute_blockchain_list" }
            return GtvArray(emptyArray())
        }

        fun queryGetConfiguration(node: Nodes, eContext: EContext, args: Gtv): Gtv {
            logger.error { "Query: nm_get_blockchain_configuration" }
            return GtvByteArray(byteArrayOf())
        }

        fun queryFindNextConfigurationHeight(node: Nodes, eContext: EContext, args: Gtv): Gtv {
            logger.error { "Query: nm_find_next_configuration_height" }
            return GtvInteger(0) // GtvNull()
        }

        private fun argCurrentHeight(args: Gtv): Long {
            return args["current_height"]!!.asInteger()
        }

        private fun peerInfoToGtv(peerInfo: PeerInfo): Gtv {
            return GtvFactory.gtv(
                    GtvFactory.gtv(peerInfo.host),
                    GtvFactory.gtv(peerInfo.port.toLong()),
                    GtvFactory.gtv(peerInfo.pubKey))
        }
    }

}

class ManagedTestModule0() : ManagedTestModule(Companion.Nodes.Node0)

class ManagedTestModule1() : ManagedTestModule(Companion.Nodes.Node1)
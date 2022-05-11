// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest.managedmode

import net.postchain.common.hexStringToByteArray
import net.postchain.core.EContext
import net.postchain.gtv.*
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtx.SimpleGTXModule
import net.postchain.integrationtest.managedmode.TestModulesHelper.argBlockchainRid
import net.postchain.integrationtest.managedmode.TestModulesHelper.argCurrentHeight
import net.postchain.integrationtest.managedmode.TestModulesHelper.argHeight
import net.postchain.integrationtest.managedmode.TestModulesHelper.peerInfoToGtv
import net.postchain.integrationtest.managedmode.TestPeerInfos.Companion.peerInfo0
import net.postchain.util.TestKLogging

open class ManagedTestModuleSinglePeerLaunchesAndStopsChains(val stage: Int) : SimpleGTXModule<Unit>(
        Unit,
        mapOf(),
        mapOf(
                "nm_get_peer_infos" to ::queryGetPeerInfos,
                "nm_get_peer_list_version" to ::queryGetPeerListVersion,
                "nm_compute_blockchain_list" to ::queryComputeBlockchainList,
                "nm_get_blockchain_configuration" to ::queryGetConfiguration,
                "nm_find_next_configuration_height" to ::queryFindNextConfigurationHeight,
                "nm_get_blockchain_replica_node_map" to ::dummyHandlerArray,
                "nm_get_node_replica_map" to ::dummyHandlerArray
        )
) {

    override fun initializeDB(ctx: EContext) {}

    companion object : TestKLogging(LogLevel.DEBUG) {

        private val BLOCKCHAIN_RIDS = mapOf(
                0L to "DB52031AB8994B26AACFB5DB01264226A6F3A73821D46C71CEA66FEB7AD38618"
                , 100L to "932380DC6DF90854B1170FCA873F1BCD3CA42F44D6423DCE96B8957B8DA1F525"
                , 101L to "3964AD3C0BB381E2E1F6F7289B4F002EEE30E5E83B67B105991BB3EE7903B45D"
        )

        private val stage0 = -1 until 5
        private val stage1 = 5 until 10
        private val stage2 = 10 until 15
        private val stage3 = 15 until 20

        fun queryGetPeerInfos(unit: Unit, eContext: EContext, args: Gtv): Gtv {
            logger.log { "Query: nm_get_peer_infos" }
            return GtvArray(arrayOf(
                    peerInfoToGtv(peerInfo0))
            )
        }

        fun queryGetPeerListVersion(unit: Unit, eContext: EContext, args: Gtv): Gtv {
            logger.log { "Query: nm_get_peer_list_version" }
            return GtvInteger(1L)
        }

        fun queryComputeBlockchainList(unit: Unit, eContext: EContext, args: Gtv): Gtv {
            logger.log { "Query: nm_compute_blockchain_list" }

            val chainIds = when (argCurrentHeight(args)) {
                in stage0 -> arrayOf(0L)
                in stage1 -> arrayOf(0L, 100L)
                in stage2 -> arrayOf(0L, 100L, 101L)
                in stage3 -> arrayOf(0L, 101L)
                else -> arrayOf(0L, 101L)
            }

            return GtvArray(
                    chainIds.map(::gtvBlockchainRid).toTypedArray())
        }

        fun queryGetConfiguration(unit: Unit, eContext: EContext, args: Gtv): Gtv {
            logger.log {
                "Query: nm_get_blockchain_configuration: " +
                        "height: ${argHeight(args)}, " +
                        "blockchainRid: ${argBlockchainRid(args)}"
            }

            val blockchainConfigFilename = when (argBlockchainRid(args).uppercase()) {
                BLOCKCHAIN_RIDS[0L] -> {
                    when (argHeight(args)) {
                        5L -> "/net/postchain/devtools/managedmode/singlepeer_launches_and_stops_chains/blockchain_config_0_height_5.xml"
                        10L -> "/net/postchain/devtools/managedmode/singlepeer_launches_and_stops_chains/blockchain_config_0_height_10.xml"
                        15L -> "/net/postchain/devtools/managedmode/singlepeer_launches_and_stops_chains/blockchain_config_0_height_15.xml"
                        else -> "an unreachable branch"
                    }
                }

                BLOCKCHAIN_RIDS[100L] -> {
                    "/net/postchain/devtools/managedmode/singlepeer_launches_and_stops_chains/blockchain_config_1.xml"
                }

                BLOCKCHAIN_RIDS[101L] -> {
                    "/net/postchain/devtools/managedmode/singlepeer_launches_and_stops_chains/blockchain_config_2.xml"
                }

                else -> "an unreachable branch"
            }

            val gtvConfig = try {
                GtvMLParser.parseGtvML(
                        javaClass.getResource(blockchainConfigFilename).readText())
            } catch (e: Exception) {
                logger.error { "Some troubles with resource loading: $blockchainConfigFilename, ${e.message}" }
                throw e
            }

            val encodedGtvConfig = GtvEncoder.encodeGtv(gtvConfig)
            return GtvFactory.gtv(encodedGtvConfig)
        }

        fun queryFindNextConfigurationHeight(unit: Unit, eContext: EContext, args: Gtv): Gtv {
            logger.log { "Query: nm_find_next_configuration_height" }
            return when (argHeight(args)) {
                in stage0 -> GtvInteger(5)
                in stage1 -> GtvInteger(10)
                in stage2 -> GtvInteger(15)
                in stage3 -> GtvNull
                else -> GtvNull
            }
        }

        private fun gtvBlockchainRid(chainId: Long): Gtv {
            return GtvFactory.gtv(
                    BLOCKCHAIN_RIDS[chainId]?.hexStringToByteArray() ?: byteArrayOf())
        }

    }
}

class ManagedTestModuleSinglePeerLaunchesAndStopsChains0 : ManagedTestModuleSinglePeerLaunchesAndStopsChains(0)

class ManagedTestModuleSinglePeerLaunchesAndStopsChains1 : ManagedTestModuleSinglePeerLaunchesAndStopsChains(1)

class ManagedTestModuleSinglePeerLaunchesAndStopsChains2 : ManagedTestModuleSinglePeerLaunchesAndStopsChains(2)

class ManagedTestModuleSinglePeerLaunchesAndStopsChains3 : ManagedTestModuleSinglePeerLaunchesAndStopsChains(3)


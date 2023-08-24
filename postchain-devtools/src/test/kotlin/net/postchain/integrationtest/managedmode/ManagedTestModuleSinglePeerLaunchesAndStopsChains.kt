// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest.managedmode

import net.postchain.base.data.DatabaseAccess
import net.postchain.common.hexStringToByteArray
import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvNull
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtx.SimpleGTXModule
import net.postchain.integrationtest.managedmode.TestModulesHelper.argBlockchainRid
import net.postchain.integrationtest.managedmode.TestModulesHelper.argHeight
import net.postchain.integrationtest.managedmode.TestModulesHelper.peerInfoToGtv
import net.postchain.integrationtest.managedmode.TestPeerInfos.Companion.peerInfo0
import net.postchain.util.TestKLogging

open class ManagedTestModuleSinglePeerLaunchesAndStopsChains(val stage: Int) : SimpleGTXModule<Unit>(
        Unit,
        mapOf(),
        mapOf(
                "nm_get_peer_infos" to ::queryGetPeerInfos,
                "nm_compute_blockchain_info_list" to ::queryComputeBlockchainInfoList,
                "nm_get_blockchain_configuration" to ::queryGetConfiguration,
                "nm_find_next_configuration_height" to ::queryFindNextConfigurationHeight,
                "nm_get_blockchain_replica_node_map" to ::dummyHandlerArray,
                "nm_get_node_replica_map" to ::dummyHandlerArray,
                "nm_api_version" to ::queryNMApiVersion
        )
) {

    override fun initializeDB(ctx: EContext) {}

    companion object : TestKLogging(LogLevel.DEBUG) {

        private val BLOCKCHAIN_RIDS = mapOf(
                0L to "74129F4DE673D2200DFC20E2D02073C03EFEDE5BCE165970C71191275B07FABD",
                100L to "B52512A7B550370E3C0B05F32B68C49A3C8F0B52C1CDAC323F3FC98514882157",
                101L to "04BA8587D4EFDA97B21611CB20504A23FD305DABFB28AC4824EEF3AB3FDD0508"
        )

        private val stage0 = -1 until 5
        private val stage1 = 5 until 10
        private val stage2 = 10 until 15
        private val stage3 = 15 until 20

        var chain101RecoveringCounter = 0

        fun queryGetPeerInfos(unit: Unit, eContext: EContext, args: Gtv): Gtv {
            logger.log { "Query: nm_get_peer_infos" }
            return GtvArray(arrayOf(
                    peerInfoToGtv(peerInfo0))
            )
        }

        fun queryNMApiVersion(unit: Unit, eContext: EContext, args: Gtv): Gtv {
            logger.log { "Query: nm_api_version" }
            return GtvInteger(4L)
        }

        fun queryComputeBlockchainInfoList(unit: Unit, eContext: EContext, args: Gtv): Gtv {
            logger.log { "Query: nm_compute_blockchain_info_list" }

            val chainIds = when (DatabaseAccess.of(eContext).getLastBlockHeight(eContext)) {
                in stage0 -> arrayOf(0L)
                in stage1 -> arrayOf(0L, 100L)
                in stage2 -> arrayOf(0L, 100L, 101L)
                in stage3 -> arrayOf(0L, 101L)
                else -> arrayOf(0L, 101L)
            }

            return GtvArray(
                    chainIds.map {
                        GtvDictionary.build(mapOf(
                                "rid" to gtvBlockchainRid(it),
                                "system" to GtvInteger(if (it == 0L) 1L else 0L)
                        ))
                    }.toTypedArray()
            )
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
                        else -> null
                    }
                }

                BLOCKCHAIN_RIDS[100L] -> {
                    "/net/postchain/devtools/managedmode/singlepeer_launches_and_stops_chains/blockchain_config_1.xml"
                }

                BLOCKCHAIN_RIDS[101L] -> {
                    when (argHeight(args)) {
                        10L -> {
                            if (chain101RecoveringCounter++ < 5) { // bad config
                                "/net/postchain/devtools/managedmode/singlepeer_launches_and_stops_chains/blockchain_config_2_bad.xml"
                            } else { // recovering config
                                "/net/postchain/devtools/managedmode/singlepeer_launches_and_stops_chains/blockchain_config_2.xml"
                            }
                        }
                        else -> "/net/postchain/devtools/managedmode/singlepeer_launches_and_stops_chains/blockchain_config_2.xml"
                    }
                }
                else -> null
            }

            val gtvConfig = try {
                GtvMLParser.parseGtvML(Companion::class.java.getResource(blockchainConfigFilename!!).readText())
            } catch (e: Exception) {
                logger.error { "Some troubles with resource loading: $blockchainConfigFilename, ${e.message}" }
                throw e
            }

            val encodedGtvConfig = GtvEncoder.encodeGtv(gtvConfig)
            return GtvFactory.gtv(encodedGtvConfig)
        }

        fun queryFindNextConfigurationHeight(unit: Unit, eContext: EContext, args: Gtv): Gtv {
            logger.log { "Query: nm_find_next_configuration_height" }

            return when (argBlockchainRid(args).uppercase()) {
                BLOCKCHAIN_RIDS[0L] -> {
                    when (argHeight(args)) {
                        in stage0 -> GtvInteger(5)
                        in stage1 -> GtvInteger(10)
                        in stage2 -> GtvInteger(15)
                        in stage3 -> GtvNull
                        else -> GtvNull
                    }
                }
                BLOCKCHAIN_RIDS[101L] -> {
                    when (argHeight(args)) {
                        in 0 until 10L -> GtvInteger(10)
                        else -> GtvNull
                    }
                }
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


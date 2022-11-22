// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.core.NodeRid
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.managed.query.QueryRunner

open class BaseManagedNodeDataSource(val queryRunner: QueryRunner, val appConfig: AppConfig)
    : ManagedNodeDataSource, QueryRunner by queryRunner {

    companion object : KLogging()

    protected val nmApiVersion by lazy {
        query("nm_api_version", buildArgs()).asInteger().toInt()
    }

    override fun getPeerInfos(): Array<PeerInfo> {
        // TODO: [POS-90]: Implement correct error processing
        val res = query("nm_get_peer_infos", buildArgs())
        return res.asArray().map { PeerInfo.fromGtv(it) }.toTypedArray()
    }

    override fun getPeerListVersion(): Long {
        val res = query("nm_get_peer_list_version", buildArgs())
        return res.asInteger()
    }

    override fun computeBlockchainList(): List<ByteArray> {
        val res = query(
                "nm_compute_blockchain_list",
                buildArgs("node_id" to GtvFactory.gtv(appConfig.pubKeyByteArray))
        )

        return res.asArray().map { it.asByteArray() }
    }

    override fun computeBlockchainInfoList(): List<BlockchainInfo> {
        return if (nmApiVersion >= 4) {
            val res = query(
                    "nm_compute_blockchain_info_list",
                    buildArgs("node_id" to GtvFactory.gtv(appConfig.pubKeyByteArray))
            )

            res.asArray().map { BlockchainInfo(BlockchainRid(it["rid"]!!.asByteArray()), it["system"]!!.asBoolean()) }
        } else {
            // Fallback for legacy API versions
            computeBlockchainList().map { BlockchainInfo(BlockchainRid(it), false) }
        }
    }

    override fun getConfiguration(blockchainRidRaw: ByteArray, height: Long): ByteArray? {
        val res = query(
                "nm_get_blockchain_configuration",
                buildArgs(
                        "blockchain_rid" to GtvFactory.gtv(blockchainRidRaw),
                        "height" to GtvFactory.gtv(height))
        )

        return if (res.isNull()) null else res.asByteArray()
    }

    override fun findNextConfigurationHeight(blockchainRidRaw: ByteArray, height: Long): Long? {
        val res = query(
                "nm_find_next_configuration_height",
                buildArgs(
                        "blockchain_rid" to GtvFactory.gtv(blockchainRidRaw),
                        "height" to GtvFactory.gtv(height))
        )

        return if (res.isNull()) null else res.asInteger()
    }

    fun buildArgs(vararg args: Pair<String, Gtv>): Gtv {
        return GtvFactory.gtv(*args)
    }

    override fun getSyncUntilHeight(): Map<BlockchainRid, Long> {
        return if (nmApiVersion >= 2) {
            val blockchains = computeBlockchainList()
            val heights = query(
                    "nm_get_blockchain_last_height_map",
                    buildArgs("blockchain_rids" to GtvFactory.gtv(
                            *(blockchains.map { GtvFactory.gtv(it) }.toTypedArray())
                    ))
            ).asArray()

            blockchains.mapIndexed { i, brid ->
                BlockchainRid(brid) to if (i < heights.size) heights[i].asInteger() else -1
            }.toMap()

        } else {
            mapOf()
        }
    }

    override fun getBlockchainReplicaNodeMap(): Map<BlockchainRid, List<NodeRid>> {
        val blockchains = computeBlockchainList()

        // Rell: query nm_get_blockchain_replica_node_map(blockchain_rids: list<byte_array>): list<list<byte_array>>
        val replicas = query(
                "nm_get_blockchain_replica_node_map",
                buildArgs("blockchain_rids" to GtvFactory.gtv(
                        *(blockchains.map { GtvFactory.gtv(it) }.toTypedArray())
                ))
        ).asArray()

        return blockchains.mapIndexed { i, brid ->
            BlockchainRid(brid) to if (i < replicas.size) {
                replicas[i].asArray().map { NodeRid(it.asByteArray()) }
            } else emptyList()
        }.toMap()
    }
}
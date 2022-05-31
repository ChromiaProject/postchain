// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import net.postchain.base.PeerInfo
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.core.block.BlockQueries
import net.postchain.core.NodeRid
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import java.time.Instant

open class BaseManagedNodeDataSource(val queries: BlockQueries, val appConfig: AppConfig) : ManagedNodeDataSource {

    protected val nmApiVersion = queries.query("nm_api_version", buildArgs()).get().asInteger().toInt()

    override fun getPeerInfos(): Array<PeerInfo> {
        // TODO: [POS-90]: Implement correct error processing
        val res = queries.query("nm_get_peer_infos", buildArgs())
        return res.get().asArray()
                .map {
                    val pia = it.asArray()
                    PeerInfo(
                            pia[0].asString(),
                            pia[1].asInteger().toInt(),
                            pia[2].asByteArray(),
                            Instant.ofEpochMilli(if (pia.size < 4) 0L else pia[3].asInteger())
                    )
                }
                .toTypedArray()
    }

    override fun getPeerListVersion(): Long {
        val res = queries.query("nm_get_peer_list_version", buildArgs())
        return res.get().asInteger()
    }

    override fun computeBlockchainList(): List<ByteArray> {
        val res = queries.query(
                "nm_compute_blockchain_list",
                buildArgs("node_id" to GtvFactory.gtv(appConfig.pubKeyByteArray))
        ).get()

        return res.asArray().map { it.asByteArray() }
    }

    override fun getConfiguration(blockchainRidRaw: ByteArray, height: Long): ByteArray? {
        val res = queries.query(
                "nm_get_blockchain_configuration",
                buildArgs(
                        "blockchain_rid" to GtvFactory.gtv(blockchainRidRaw),
                        "height" to GtvFactory.gtv(height))
        ).get()

        return if (res.isNull()) null else res.asByteArray()
    }

    override fun getConfigurations(blockchainRidRaw: ByteArray): Map<Long, ByteArray> {
        val res = queries.query(
                "nm_get_blockchain_configurations",
                buildArgs("blockchain_rid" to GtvFactory.gtv(blockchainRidRaw))
        ).get()

        return res.asArray()
                .associate { heightConfig -> heightConfig[0].asInteger() to heightConfig[1].asByteArray() }
    }

    override fun findNextConfigurationHeight(blockchainRidRaw: ByteArray, height: Long): Long? {
        val res = queries.query(
                "nm_find_next_configuration_height",
                buildArgs(
                        "blockchain_rid" to GtvFactory.gtv(blockchainRidRaw),
                        "height" to GtvFactory.gtv(height))
        ).get()

        return if (res.isNull()) null else res.asInteger()
    }

    fun buildArgs(vararg args: Pair<String, Gtv>): Gtv {
        return GtvFactory.gtv(*args)
    }

    override fun getSyncUntilHeight(): Map<BlockchainRid, Long> {
        return if (nmApiVersion >= 2) {
            val blockchains = computeBlockchainList()
            val heights = queries.query(
                    "nm_get_blockchain_last_height_map",
                    buildArgs("blockchain_rids" to GtvFactory.gtv(
                            *(blockchains.map { GtvFactory.gtv(it) }.toTypedArray())
                    ))
            ).get().asArray()

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
        val replicas = queries.query(
                "nm_get_blockchain_replica_node_map",
                buildArgs("blockchain_rids" to GtvFactory.gtv(
                        *(blockchains.map { GtvFactory.gtv(it) }.toTypedArray())
                ))
        ).get().asArray()

        return blockchains.mapIndexed { i, brid ->
            BlockchainRid(brid) to if (i < replicas.size) {
                replicas[i].asArray().map { NodeRid(it.asByteArray()) }
            } else emptyList()
        }.toMap()
    }

    override fun getNodeReplicaMap(): Map<NodeRid, List<NodeRid>> {
        // Rell: query nm_get_node_replica_map(): list<list<byte_array>>
        // [ [ key_peer_id, replica_peer_id_1, replica_peer_id_2, ...], ...]
        val peersReplicas = queries.query(
                "nm_get_node_replica_map",
                buildArgs()
        ).get().asArray()

        return peersReplicas
                .map(Gtv::asArray)
                .filter { it.isNotEmpty() }
                .map { it -> it.map { NodeRid(it.asByteArray()) } }
                .map { peerAndReplicas_ ->
                    peerAndReplicas_.first() to peerAndReplicas_.drop(1)
                }.toMap()
    }
}
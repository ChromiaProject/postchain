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
import net.postchain.utils.KovenantHelper
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

open class BaseManagedNodeDataSource(val queryRunner: QueryRunner, val appConfig: AppConfig) : ManagedNodeDataSource {

    companion object : KLogging()

    protected val context = KovenantHelper.createContext("ManagedDataSource", appConfig.databaseReadConcurrency)

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

    override fun query(name: String, args: Gtv): Gtv {
        return queryRunner.query(name, args)
    }

    override fun queryAsync(name: String, args: Gtv): Promise<Gtv, Exception> {
        return task(context) {
            queryRunner.query(name, args)
        }
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

    override fun getNodeReplicaMap(): Map<NodeRid, List<NodeRid>> {
        // Rell: query nm_get_node_replica_map(): list<list<byte_array>>
        // [ [ key_peer_id, replica_peer_id_1, replica_peer_id_2, ...], ...]
        val peersReplicas = query(
                "nm_get_node_replica_map",
                buildArgs()
        ).asArray()

        return peersReplicas
                .map(Gtv::asArray)
                .filter { it.isNotEmpty() }
                .map { it -> it.map { NodeRid(it.asByteArray()) } }
                .map { peerAndReplicas_ ->
                    peerAndReplicas_.first() to peerAndReplicas_.drop(1)
                }.toMap()
    }
}
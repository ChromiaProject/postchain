package net.postchain.d1.icmf

import mu.KLogging
import net.postchain.base.SpecialTransactionPosition
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockEContext
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.PubKey
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.OpData
import net.postchain.gtx.special.GTXSpecialTxExtension

class IcmfRemoteSpecialTxExtension : GTXSpecialTxExtension {

    companion object : KLogging() {
        const val OP_ICMF_MESSAGE = "__icmf_message"
    }

    private val _relevantOps = setOf(OP_ICMF_MESSAGE)

    override fun init(module: GTXModule, chainID: Long, blockchainRID: BlockchainRid, cs: CryptoSystem) {

    }

    override fun getRelevantOps() = _relevantOps

    override fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean = when (position) {
        SpecialTransactionPosition.Begin -> true
        SpecialTransactionPosition.End -> false
    }

    /**
     * I am block builder, go fetch messages.
     */
    override fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext): List<OpData> {
        val allClusters: Set<D1ClusterInfo> = lookupAllClustersInD1()

        val topic: String = "???"
        val lastHeight: Long = 0

        // TODO parallelize this
        for (cluster in allClusters) {
            val anchoringClient = ConcretePostchainClientProvider().createClient(
                PostchainClientConfig(
                    cluster.anchoringChain,
                    EndpointPool.default(cluster.peers.map { it.restApiUrl })
                )
            )
            // query icmf_get_messages_hash_since_height(topic: text, anchor_height: integer): list<struct<icmf_messages_hash>>
            val packets = anchoringClient.querySync(
                "icmf_get_messages_hash_since_height",
                gtv(mapOf("topic" to gtv(topic), "anchor_height" to gtv(lastHeight)))
            ).asArray().map { Packet.fromGtv(it) }
            for (packet in packets) {
                packet.
            }
        }
    }

    private fun lookupAllClustersInD1(): Set<D1ClusterInfo> = TODO("Not yet implemented")

    /**
     * I am validator, validate messages.
     */
    override fun validateSpecialOperations(
        position: SpecialTransactionPosition,
        bctx: BlockEContext,
        ops: List<OpData>
    ): Boolean {
        TODO("Not yet implemented")
    }

    data class D1ClusterInfo(val name: String, val anchoringChain: BlockchainRid, val peers: Set<D1PeerInfo>)

    data class D1PeerInfo(val restApiUrl: String, val pubKey: PubKey) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as D1PeerInfo

            if (restApiUrl != other.restApiUrl) return false

            return true
        }

        override fun hashCode(): Int {
            return restApiUrl.hashCode()
        }
    }

    data class Packet(val sender: BlockchainRid, val senderHeight: Long, val hash: ByteArray) {
        companion object {
            fun fromGtv(gtv: Gtv) = Packet(
                BlockchainRid(gtv["sender"]!!.asByteArray()),
                gtv["sender_height"]!!.asInteger(),
                gtv["hash"]!!.asByteArray()
            )
        }
    }
}

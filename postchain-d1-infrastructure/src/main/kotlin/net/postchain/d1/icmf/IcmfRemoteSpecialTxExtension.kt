package net.postchain.d1.icmf

import mu.KLogging
import net.postchain.base.BaseBlockWitness
import net.postchain.base.SpecialTransactionPosition
import net.postchain.base.gtv.BlockHeaderDataFactory
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockEContext
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.PubKey
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.OpData
import net.postchain.gtx.special.GTXSpecialTxExtension

class IcmfRemoteSpecialTxExtension : GTXSpecialTxExtension {

    companion object : KLogging() {
        const val OP_ICMF_MESSAGE = "__icmf_message"
    }

    private val _relevantOps = setOf(OP_ICMF_MESSAGE)
    private lateinit var cryptoSystem: CryptoSystem

    override fun init(module: GTXModule, chainID: Long, blockchainRID: BlockchainRid, cs: CryptoSystem) {
        cryptoSystem = cs
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
            fetchMessagesFromCluster(cluster, topic, lastHeight)
        }
        return listOf()
    }

    private fun fetchMessagesFromCluster(
        cluster: D1ClusterInfo,
        topic: String,
        lastHeight: Long
    ): List<IcmfMessage> {
        val anchoringClient = ConcretePostchainClientProvider().createClient(
            PostchainClientConfig(
                cluster.anchoringChain,
                EndpointPool.default(cluster.peers.map { it.restApiUrl })
            )
        )
        // query icmf_get_headers_with_messages_since_height(topic: text, anchor_height: integer): list<signed_block_header>
        val signedBlockHeaders = anchoringClient.querySync(
            "icmf_get_headers_with_messages_since_height",
            gtv(mapOf("topic" to gtv(topic), "anchor_height" to gtv(lastHeight)))
        ).asArray().map { SignedBlockHeader.fromGtv(it) }

        val messages = mutableListOf<IcmfMessage>()
        for (header in signedBlockHeaders) {
            val decodedHeader = BlockHeaderDataFactory.buildFromBinary(header.rawHeader)
            val witness = BaseBlockWitness.fromBytes(header.rawWitness)
            val blockRid = decodedHeader.toGtv().merkleHash(GtvMerkleHashCalculator(cryptoSystem))

            if (!witness.getSignatures().all { cryptoSystem.verifyDigest(blockRid, it) }) {
                logger.warn("Invalid block header signature for block-rid: $blockRid for blockchain-rid: ${decodedHeader.gtvBlockchainRid} at height: ${decodedHeader.gtvHeight}")
                return listOf()
            }

            val icmfHeaderData = decodedHeader.getExtra()[ICMF_BLOCK_HEADER_EXTRA]
        }
        return messages
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

    data class SignedBlockHeader(val rawHeader: ByteArray, val rawWitness: ByteArray) {
        companion object {
            fun fromGtv(gtv: Gtv) = SignedBlockHeader(
                gtv["block_header"]!!.asByteArray(),
                gtv["witness"]!!.asByteArray()
            )
        }
    }
}

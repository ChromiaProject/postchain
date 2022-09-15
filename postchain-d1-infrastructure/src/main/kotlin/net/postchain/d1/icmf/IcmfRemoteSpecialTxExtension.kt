package net.postchain.d1.icmf

import mu.KLogging
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.BaseBlockWitness
import net.postchain.base.SpecialTransactionPosition
import net.postchain.base.data.DatabaseAccess
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
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ScalarHandler

class IcmfRemoteSpecialTxExtension(private val topics: List<String>) : GTXSpecialTxExtension {

    companion object : KLogging() {
        // operation __icmf_message(sender: byte_array, topic: text, body: gtv)
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

        // TODO parallelize this
        val allMessages = mutableListOf<IcmfMessage>()
        for (cluster in allClusters) {
            val messages = try {
                fetchMessagesFromCluster(cluster, bctx)
            } catch (e: Exception) {
                logger.error(e) { "Unable to fetch ICMF messages from cluster ${cluster.name}" }
                listOf()
            }
            allMessages.addAll(messages)
        }
        return allMessages.map { OpData(OP_ICMF_MESSAGE, arrayOf(gtv(it.sender.data), gtv(it.topic), it.body)) }
    }

    private fun fetchMessagesFromCluster(
        cluster: D1ClusterInfo,
        bctx: BlockEContext
    ): List<IcmfMessage> {
        val lastAnchorHeight = DatabaseAccess.of(bctx).let {
            val queryRunner = QueryRunner()
            queryRunner.query(
                bctx.conn,
                "SELECT height FROM ${it.tableAnchorHeight(bctx)} WHERE cluster = ?",
                ScalarHandler<Long>(),
                cluster.name
            ) ?: -1
        }

        val anchoringClient = ConcretePostchainClientProvider().createClient(
            PostchainClientConfig(
                cluster.anchoringChain,
                EndpointPool.default(cluster.peers.map { it.restApiUrl })
            )
        )

        val currentAnchorHeight = anchoringClient.currentBlockHeightSync()

        val messages = mutableListOf<IcmfMessage>()
        for (topic in topics) {
            // query icmf_get_headers_with_messages_between_heights(topic: text, from_anchor_height: integer, to_anchor_height: integer): list<signed_block_header_with_anchor_height>
            val signedBlockHeaderWithAnchorHeights = anchoringClient.querySync(
                "icmf_get_headers_with_messages_between_heights",
                gtv(mapOf("topic" to gtv(topic), "from_anchor_height" to gtv(lastAnchorHeight + 1), "to_anchor_height" to gtv(currentAnchorHeight)))
            ).asArray().map { SignedBlockHeaderWithAnchorHeight.fromGtv(it) }

            for (header in signedBlockHeaderWithAnchorHeights) {
                val decodedHeader = BlockHeaderData.fromBinary(header.rawHeader)
                val witness = BaseBlockWitness.fromBytes(header.rawWitness)
                val blockRid = decodedHeader.toGtv().merkleHash(GtvMerkleHashCalculator(cryptoSystem))

                if (!witness.getSignatures().all { cryptoSystem.verifyDigest(blockRid, it) }) {
                    logger.warn("Invalid block header signature for block-rid: $blockRid for blockchain-rid: ${decodedHeader.getBlockchainRid()} at height: ${decodedHeader.getHeight()}")
                    return listOf()
                }

                val icmfHeaderData = decodedHeader.getExtra()[ICMF_BLOCK_HEADER_EXTRA]
                if (icmfHeaderData == null) {
                    logger.warn("$ICMF_BLOCK_HEADER_EXTRA block header extra data missing for block-rid: $blockRid for blockchain-rid: ${decodedHeader.getBlockchainRid()} at height: ${decodedHeader.getHeight()}")
                    return listOf()
                }

                val topicData = icmfHeaderData[topic]?.let { TopicHeaderData.fromGtv(it) }
                if (topicData == null) {
                    logger.warn("$ICMF_BLOCK_HEADER_EXTRA header extra data missing topic $topic for block-rid: $blockRid for blockchain-rid: ${decodedHeader.getBlockchainRid()} at height: ${decodedHeader.getHeight()}")
                    return listOf()
                }

                DatabaseAccess.of(bctx).apply {
                    val queryRunner = QueryRunner()
                    val prevMessageBlockHeight = queryRunner.query(
                        bctx.conn,
                        "SELECT height FROM ${tableMessageHeight(bctx)} WHERE cluster = ? AND topic = ?",
                        ScalarHandler<Long>(),
                        cluster.name,
                        topic
                    )

                    if (topicData.prevMessageBlockHeight != prevMessageBlockHeight) {
                        logger.warn("$ICMF_BLOCK_HEADER_EXTRA header extra has incorrect previous message height ${topicData.prevMessageBlockHeight}, expected $prevMessageBlockHeight for topic $topic for block-rid: $blockRid for blockchain-rid: ${decodedHeader.getBlockchainRid()} at height: ${decodedHeader.getHeight()}")
                        return listOf()
                    }

                    queryRunner.update(
                        bctx.conn,
                        "INSERT INTO ${tableMessageHeight(bctx)} (cluster, topic, height) VALUES (?, ?, ?) ON CONFLICT (cluster, topic) DO UPDATE SET height = ?",
                        cluster.name,
                        topic,
                        decodedHeader.getHeight(),
                        decodedHeader.getHeight()
                    )
                }

                val client = ConcretePostchainClientProvider().createClient(
                    PostchainClientConfig(
                        BlockchainRid(decodedHeader.getBlockchainRid()),
                        EndpointPool.default(cluster.peers.map { it.restApiUrl })
                    )
                )
                // query icmf_get_messages(topic: text, height: integer): list<gtv>
                val bodies = client.querySync(
                    "icmf_get_messages",
                    gtv(mapOf("topic" to gtv(topic), "height" to gtv(decodedHeader.getHeight())))
                ).asArray()

                val computedHash = cryptoSystem.digest(bodies.map { cryptoSystem.digest(it.asByteArray()) }.fold(ByteArray(0)) { total, item ->
                    total.plus(item)
                })
                if (topicData.hash.contentEquals(computedHash)) {
                    logger.warn("invalid messages hash for topic: $topic for block-rid: $blockRid for blockchain-rid: ${decodedHeader.getBlockchainRid()} at height: ${decodedHeader.getHeight()}")
                    return listOf()
                }

                for (body in bodies) {
                    messages.add(IcmfMessage(BlockchainRid(decodedHeader.getBlockchainRid()), decodedHeader.getHeight(), topic, body))
                }
            }
        }

        if (currentAnchorHeight > lastAnchorHeight) {
            DatabaseAccess.of(bctx).apply {
                val queryRunner = QueryRunner()
                queryRunner.update(
                    bctx.conn,
                    "INSERT INTO ${tableAnchorHeight(bctx)} (cluster, height) VALUES (?, ?) ON CONFLICT (cluster) DO UPDATE SET height = ?",
                    cluster.name,
                    currentAnchorHeight,
                    currentAnchorHeight
                )
            }
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

    data class SignedBlockHeaderWithAnchorHeight(val rawHeader: ByteArray, val rawWitness: ByteArray, val anchorHeight: Long) {
        companion object {
            fun fromGtv(gtv: Gtv) = SignedBlockHeaderWithAnchorHeight(
                gtv["block_header"]!!.asByteArray(),
                gtv["witness"]!!.asByteArray(),
                gtv["anchor_height"]!!.asInteger()
            )
        }
    }
}

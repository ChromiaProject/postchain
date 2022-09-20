package net.postchain.d1.icmf

import mu.KLogging
import net.postchain.base.BaseBlockWitness
import net.postchain.base.SpecialTransactionPosition
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.core.BlockEContext
import net.postchain.crypto.CryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.OpData
import net.postchain.gtx.special.GTXSpecialTxExtension
import net.postchain.managed.DirectoryComponent
import net.postchain.managed.DirectoryDataSource
import net.postchain.managed.directory1.D1ClusterInfo
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ScalarHandler

class IcmfRemoteSpecialTxExtension(private val topics: List<String>) : GTXSpecialTxExtension, DirectoryComponent {

    companion object : KLogging() {
        // operation __icmf_header(block_header: byte_array, witness: byte_array)
        const val OP_ICMF_HEADER = "__icmf_header"

        // operation __icmf_message(sender: byte_array, topic: text, body: gtv)
        const val OP_ICMF_MESSAGE = "__icmf_message"
    }

    private val _relevantOps = setOf(OP_ICMF_HEADER, OP_ICMF_MESSAGE)
    private lateinit var cryptoSystem: CryptoSystem
    override lateinit var directoryDataSource: DirectoryDataSource

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
        val allOps = mutableListOf<OpData>()
        for (cluster in allClusters) {
            val ops = try {
                fetchMessagesFromCluster(cluster, bctx)
            } catch (e: Exception) {
                logger.error(e) { "Unable to fetch ICMF messages from cluster ${cluster.name}" }
                listOf()
            }
            allOps.addAll(ops)
        }
        return allOps
    }

    private fun fetchMessagesFromCluster(
            cluster: D1ClusterInfo,
            bctx: BlockEContext
    ): List<OpData> {
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

        val ops = mutableListOf<OpData>()
        for (topic in topics) {
            // query icmf_get_headers_with_messages_between_heights(topic: text, from_anchor_height: integer, to_anchor_height: integer): list<signed_block_header_with_anchor_height>
            val signedBlockHeaderWithAnchorHeights = anchoringClient.querySync(
                    "icmf_get_headers_with_messages_between_heights",
                    gtv(
                            mapOf(
                                    "topic" to gtv(topic),
                                    "from_anchor_height" to gtv(lastAnchorHeight + 1),
                                    "to_anchor_height" to gtv(currentAnchorHeight)
                            )
                    )
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

                if (!validatePrevMessageHeight(bctx, decodedHeader.getBlockchainRid(), topic, topicData, decodedHeader.getHeight())) return listOf()

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

                val computedHash = cryptoSystem.digest(bodies.map { cryptoSystem.digest(it.asByteArray()) }
                        .fold(ByteArray(0)) { total, item ->
                            total.plus(item)
                        })
                if (!topicData.hash.contentEquals(computedHash)) {
                    logger.warn("invalid messages hash for topic: $topic for block-rid: $blockRid for blockchain-rid: ${decodedHeader.getBlockchainRid()} at height: ${decodedHeader.getHeight()}")
                    return listOf()
                }

                ops.add(OpData(OP_ICMF_HEADER, arrayOf(gtv(header.rawHeader), gtv(header.rawWitness))))

                for (body in bodies) {
                    ops.add(
                            OpData(
                                    OP_ICMF_MESSAGE,
                                    arrayOf(decodedHeader.gtvBlockchainRid, gtv(topic), body)
                            )
                    )
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

        return ops
    }

    private fun lookupAllClustersInD1(): Set<D1ClusterInfo> {
        return directoryDataSource.getAllClusters().toSet()
    }

    data class SignedBlockHeaderWithAnchorHeight(
            val rawHeader: ByteArray,
            val rawWitness: ByteArray,
            val anchorHeight: Long
    ) {
        companion object {
            fun fromGtv(gtv: Gtv) = SignedBlockHeaderWithAnchorHeight(
                    gtv["block_header"]!!.asByteArray(),
                    gtv["witness"]!!.asByteArray(),
                    gtv["anchor_height"]!!.asInteger()
            )
        }
    }

    data class HeaderValidationInfo(
            val height: Long,
            val sender: ByteArray,
            val icmfHeaderData: Map<String, TopicHeaderData>
    )

    /**
     * I am validator, validate messages.
     */
    override fun validateSpecialOperations(
            position: SpecialTransactionPosition,
            bctx: BlockEContext,
            ops: List<OpData>
    ): Boolean {
        var currentHeaderData: HeaderValidationInfo? = null
        val messageHashes: MutableMap<String, MutableList<ByteArray>> = mutableMapOf()
        for (op in ops) {
            when (op.opName) {
                OP_ICMF_HEADER -> {
                    if (!validateMessages(messageHashes, currentHeaderData, bctx)) return false
                    messageHashes.clear()

                    val rawHeader = op.args[0].asByteArray()
                    val rawWitness = op.args[1].asByteArray()

                    val decodedHeader = BlockHeaderData.fromBinary(rawHeader)
                    val witness = BaseBlockWitness.fromBytes(rawWitness)
                    val blockRid = decodedHeader.toGtv().merkleHash(GtvMerkleHashCalculator(cryptoSystem))

                    if (!witness.getSignatures().all { cryptoSystem.verifyDigest(blockRid, it) }) {
                        logger.warn("Invalid block header signature for block-rid: $blockRid for blockchain-rid: ${decodedHeader.getBlockchainRid()} at height: ${decodedHeader.getHeight()}")
                        return false
                    }

                    val icmfHeaderData = decodedHeader.getExtra()[ICMF_BLOCK_HEADER_EXTRA]
                    if (icmfHeaderData == null) {
                        logger.warn("$ICMF_BLOCK_HEADER_EXTRA block header extra data missing for block-rid: $blockRid for blockchain-rid: ${decodedHeader.getBlockchainRid()} at height: ${decodedHeader.getHeight()}")
                        return false
                    }

                    currentHeaderData = HeaderValidationInfo(decodedHeader.getHeight(), decodedHeader.getBlockchainRid(), icmfHeaderData.asDict().mapValues { TopicHeaderData.fromGtv(it.value) })
                }

                OP_ICMF_MESSAGE -> {
                    val sender = op.args[0].asByteArray()
                    val topic = op.args[1].asString()
                    val body = op.args[2]

                    if (currentHeaderData == null) {
                        logger.warn("got $OP_ICMF_MESSAGE before any $OP_ICMF_HEADER")
                        return false
                    }

                    val topicData = currentHeaderData.icmfHeaderData[topic]
                    if (topicData == null) {
                        logger.warn("$ICMF_BLOCK_HEADER_EXTRA header extra data missing topic $topic for sender ${sender.toHex()}")
                        return false
                    }

                    messageHashes.computeIfAbsent(topic) { mutableListOf() }
                            .add(cryptoSystem.digest(body.asByteArray()))
                }
            }
        }
        return validateMessages(messageHashes, currentHeaderData, bctx)
    }

    private fun validateMessages(
            messageHashes: MutableMap<String, MutableList<ByteArray>>,
            currentHeaderData: HeaderValidationInfo?,
            bctx: BlockEContext
    ): Boolean {
        if (!validateMessagesHash(messageHashes, currentHeaderData)) return false
        if (currentHeaderData != null) {
            for ((topic, data) in currentHeaderData.icmfHeaderData) {
                if (!validatePrevMessageHeight(
                                bctx,
                                currentHeaderData.sender,
                                topic,
                                data,
                                currentHeaderData.height
                        )
                ) return false
            }
        }
        return true
    }

    private fun validatePrevMessageHeight(
            bctx: BlockEContext,
            sender: ByteArray,
            topic: String,
            topicData: TopicHeaderData,
            height: Long
    ): Boolean {
        DatabaseAccess.of(bctx).apply {
            val queryRunner = QueryRunner()
            val prevMessageBlockHeight = queryRunner.query(
                    bctx.conn,
                    "SELECT height FROM ${tableMessageHeight(bctx)} WHERE sender = ? AND topic = ?",
                    ScalarHandler<Long>(),
                    sender,
                    topic
            )

            if (topicData.prevMessageBlockHeight != prevMessageBlockHeight) {
                logger.warn("$ICMF_BLOCK_HEADER_EXTRA header extra has incorrect previous message height ${topicData.prevMessageBlockHeight}, expected $prevMessageBlockHeight for topic $topic for sender ${sender.toHex()}")
                return false
            }

            queryRunner.update(
                    bctx.conn,
                    "INSERT INTO ${tableMessageHeight(bctx)} (sender, topic, height) VALUES (?, ?, ?) ON CONFLICT (sender, topic) DO UPDATE SET height = ?",
                    sender,
                    topic,
                    height,
                    height
            )
        }
        return true
    }

    private fun validateMessagesHash(
            messageHashes: MutableMap<String, MutableList<ByteArray>>,
            headerData: HeaderValidationInfo?
    ): Boolean {
        for ((topic, hashes) in messageHashes) {
            if (headerData == null) {
                logger.error("got $OP_ICMF_MESSAGE before any $OP_ICMF_HEADER")
                return false
            }

            val topicData = headerData.icmfHeaderData[topic]
            if (topicData == null) {
                logger.error("$ICMF_BLOCK_HEADER_EXTRA header extra data missing topic $topic")
                return false
            }

            val computedHash = cryptoSystem.digest(hashes
                    .fold(ByteArray(0)) { total, item ->
                        total.plus(item)
                    })
            if (!topicData.hash.contentEquals(computedHash)) {
                logger.warn("invalid messages hash for topic: $topic")
                return false
            }
        }
        return true
    }
}

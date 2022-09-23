package net.postchain.d1.icmf

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mu.KLogging
import net.postchain.base.BaseBlockWitness
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.PostchainClientProvider
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.BlockEContext
import net.postchain.core.Shutdownable
import net.postchain.crypto.CryptoSystem
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

class ClusterGlobalTopicPipe(override val route: GlobalTopicRoute,
                             val clusterName: String,
                             private val cryptoSystem: CryptoSystem,
                             lastAnchorHeight: Long,
                             private val postchainClientProvider: PostchainClientProvider,
                             private val clusterManagement: ClusterManagement,
                             _lastMessageHeights: List<Pair<BlockchainRid, Long>>) : IcmfPipe<GlobalTopicRoute, Long>, Shutdownable {
    companion object : KLogging() {
        val pollInterval = 10.seconds
        const val maxQueueSizeBytes = 10 * 1024 * 1024 // 10 MiB
    }

    private val packets = ConcurrentSkipListMap<Long, IcmfPackets<Long>>()
    private val currentQueueSizeBytes = AtomicInteger(0)
    private val lastAnchorHeight = AtomicLong(lastAnchorHeight)
    private val lastMessageHeights: ConcurrentMap<BlockchainRid, Long> = ConcurrentHashMap()

    init {
        _lastMessageHeights.forEach { lastMessageHeights[it.first] = it.second }
    }

    private val job: Job = CoroutineScope(Dispatchers.IO).launch(CoroutineName("pipe-worker-cluster-$clusterName-topic-${route.topic}")) {
        while (isActive) {
            try {
                backgroundFetch()
            } catch (e: Exception) {
                logger.error("Background fetch from cluster failed: ${e.message}", e)
            }
            delay(pollInterval)
        }
    }

    private fun backgroundFetch() {
        logger.info("Fetching messages")

        val cluster = clusterManagement.getClusterInfo(clusterName)

        // TODO use net.postchain.client.chromia.ChromiaClientProvider
        val anchoringClient = postchainClientProvider.createClient(
                PostchainClientConfig(
                        cluster.anchoringChain,
                        EndpointPool.default(cluster.peers.map { it.restApiUrl })
                )
        )

        val currentAnchorHeight = anchoringClient.currentBlockHeightSync()

        val currentPackets = mutableListOf<IcmfPacket>()

        // query icmf_get_headers_with_messages_between_heights(topic: text, from_anchor_height: integer, to_anchor_height: integer): list<signed_block_header_with_anchor_height>
        val signedBlockHeaderWithAnchorHeights = try {
            anchoringClient.querySync(
                    "icmf_get_headers_with_messages_between_heights",
                    gtv(
                            mapOf(
                                    "topic" to gtv(route.topic),
                                    "from_anchor_height" to gtv(lastAnchorHeight.get() + 1),
                                    "to_anchor_height" to gtv(currentAnchorHeight)
                            )
                    )
            ).asArray().map { SignedBlockHeaderWithAnchorHeight.fromGtv(it) }
        } catch (e: Exception) {
            when (e) {
                is UserMistake, is IOException -> {
                    logger.warn("Unable to query for messages on anchor chain: ${e.message}", e)
                    return
                }

                else -> throw e
            }
        }

        for (header in signedBlockHeaderWithAnchorHeights) {
            val decodedHeader = BlockHeaderData.fromBinary(header.rawHeader)
            val witness = BaseBlockWitness.fromBytes(header.rawWitness)
            val blockRid = decodedHeader.toGtv().merkleHash(GtvMerkleHashCalculator(cryptoSystem))

            val chainPeers = clusterManagement.getBlockchainPeers(BlockchainRid(decodedHeader.getBlockchainRid()), decodedHeader.getHeight())

            if (!Validation.validateBlockSignatures(cryptoSystem, decodedHeader.getPreviousBlockRid(), header.rawHeader, blockRid, chainPeers.map { it.pubkey }, witness)) {
                logger.warn("Invalid block header signature for block-rid: ${blockRid.toHex()} for blockchain-rid: ${decodedHeader.getBlockchainRid().toHex()} at height: ${decodedHeader.getHeight()}")
                return
            }

            val icmfHeaderData = decodedHeader.getExtra()[ICMF_BLOCK_HEADER_EXTRA]
            if (icmfHeaderData == null) {
                logger.warn("$ICMF_BLOCK_HEADER_EXTRA block header extra data missing for block-rid: ${blockRid.toHex()} for blockchain-rid: ${decodedHeader.getBlockchainRid().toHex()} at height: ${decodedHeader.getHeight()}")
                return
            }

            val topicData = icmfHeaderData[route.topic]?.let { TopicHeaderData.fromGtv(it) }
            if (topicData == null) {
                logger.warn("$ICMF_BLOCK_HEADER_EXTRA header extra data missing topic ${route.topic} for block-rid: ${blockRid.toHex()} for blockchain-rid: ${decodedHeader.getBlockchainRid().toHex()} at height: ${decodedHeader.getHeight()}")
                return
            }

            val currentPrevMessageBlockHeight = lastMessageHeights[BlockchainRid(decodedHeader.getPreviousBlockRid())]
                    ?: -1
            if (decodedHeader.getHeight() <= currentPrevMessageBlockHeight) {
                continue // already processed in previous block, skip it here
            } else if (topicData.prevMessageBlockHeight != currentPrevMessageBlockHeight) {
                logger.warn("$ICMF_BLOCK_HEADER_EXTRA header extra has incorrect previous message height ${topicData.prevMessageBlockHeight}, expected $currentPrevMessageBlockHeight for sender ${decodedHeader.getBlockchainRid().toHex()}")
                return
            }

            // TODO use net.postchain.client.chromia.ChromiaClientProvider
            val client = postchainClientProvider.createClient(
                    PostchainClientConfig(
                            BlockchainRid(decodedHeader.getBlockchainRid()),
                            EndpointPool.default(cluster.peers.map { it.restApiUrl })
                    )
            )
            // query icmf_get_messages(topic: text, height: integer): list<gtv>
            val bodies = try {
                client.querySync(
                        "icmf_get_messages",
                        gtv(mapOf("topic" to gtv(route.topic), "height" to gtv(decodedHeader.getHeight())))
                ).asArray()
            } catch (e: Exception) {
                when (e) {
                    is UserMistake, is IOException -> {
                        if (!clusterManagement.getActiveBlockchains(clusterName).contains(BlockchainRid(decodedHeader.getBlockchainRid()))) {
                            // chain is permanently stopped
                            logger.info("Blockchain with blockchain-rid: ${decodedHeader.getBlockchainRid().toHex()} is permanently stopped: ${e.message}")
                            continue
                        }
                        logger.warn(
                                "Unable to query blockchain with blockchain-rid: ${decodedHeader.getBlockchainRid().toHex()} for messages: ${e.message}",
                                e
                        )
                        return
                    }

                    else -> throw e
                }
            }

            val computedHash = cryptoSystem.digest(bodies.map { cryptoSystem.digest(GtvEncoder.encodeGtv(it)) }
                    .fold(ByteArray(0)) { total, item ->
                        total.plus(item)
                    })

            if (topicData.hash.contentEquals(computedHash)) {
                currentPackets.add(
                        IcmfPacket(
                                height = decodedHeader.getHeight(),
                                sender = BlockchainRid(decodedHeader.getBlockchainRid()),
                                topic = route.topic,
                                blockRid = blockRid,
                                rawHeader = header.rawHeader,
                                rawWitness = header.rawWitness,
                                prevMessageBlockHeight = topicData.prevMessageBlockHeight,
                                bodies = bodies.asList()
                        )
                )
                lastMessageHeights[BlockchainRid(decodedHeader.getPreviousBlockRid())] = decodedHeader.getHeight()
            } else {
                logger.warn("invalid messages hash for block-rid: ${blockRid.toHex()} for blockchain-rid: ${decodedHeader.getBlockchainRid().toHex()} at height: ${decodedHeader.getHeight()}")
                return // TODO Should we retry fetching of messages from another node?
            }
        }

        val packetsSizeBytes = currentPackets.sumOf { it.bodies.sumOf { body -> GtvEncoder.encodeGtv(body).size } }
        if (packets.isEmpty() || currentQueueSizeBytes.get() + packetsSizeBytes <= maxQueueSizeBytes) {
            packets[currentAnchorHeight] = IcmfPackets(currentAnchorHeight, currentPackets, packetsSizeBytes)
            currentQueueSizeBytes.addAndGet(packetsSizeBytes)

            lastAnchorHeight.set(currentAnchorHeight)
        } else {
            logger.info("pipe reached max capacity $maxQueueSizeBytes bytes")
        }

        logger.info("Fetched messages")
    }

    override fun mightHaveNewPackets(): Boolean = packets.isNotEmpty()

    override fun fetchNext(currentPointer: Long): IcmfPackets<Long>? =
            packets.higherEntry(currentPointer)?.value

    override fun markTaken(currentPointer: Long, bctx: BlockEContext) {
        bctx.addAfterCommitHook {
            packets.remove(currentPointer)?.let {
                currentQueueSizeBytes.addAndGet(-it.sizeBytes)
            }
        }
    }

    override fun shutdown() {
        job.cancel()
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

        fun toGtv(): Gtv {
            return gtv(mapOf(
                    "block_header" to gtv(rawHeader),
                    "witness" to gtv(rawWitness),
                    "anchor_height" to gtv(anchorHeight)
            ))
        }
    }
}

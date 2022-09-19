package net.postchain.d1.icmf

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KLogging
import net.postchain.base.BaseBlockWitness
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockEContext
import net.postchain.core.Shutdownable
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.PubKey
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong

class GlobalTopicPipe(override val route: GlobalTopicsRoute, override val id: String, private val cryptoSystem: CryptoSystem) : IcmfPipe<GlobalTopicsRoute, String, Long>, Shutdownable {
    companion object : KLogging()

    // TODO we need Storage here to be able to make DB operations from background process

    private val clusterName = id
    private val packets = ConcurrentSkipListMap<Long, IcmfPackets<Long>>() // TODO Set a maximum capacity?
    private val lastAnchorHeight = AtomicLong(loadLastAnchoredHeight(clusterName))
    private val job: Job = CoroutineScope(Dispatchers.IO).launch(CoroutineName("pipe-worker")) {
        while (true) {
            backgroundFetch()
            delay(1000)
        }
    }

    private fun loadLastAnchoredHeight(clusterName: String): Long = TODO("loadLastAnchoredHeight")

    private fun backgroundFetch() {
        logger.info("Fetching messages from $clusterName")

        val cluster = fetchClusterInfoFromD1(clusterName)

        val anchoringClient = ConcretePostchainClientProvider().createClient(
                PostchainClientConfig(
                        cluster.anchoringChain,
                        EndpointPool.default(cluster.peers.map { it.restApiUrl })
                )
        )

        val currentAnchorHeight = anchoringClient.currentBlockHeightSync()

        val currentPackets = mutableListOf<IcmfPacket>()

        for (topic in route.topics) {
            // query icmf_get_headers_with_messages_between_heights(topic: text, from_anchor_height: integer, to_anchor_height: integer): list<signed_block_header_with_anchor_height>
            val signedBlockHeaderWithAnchorHeights = anchoringClient.querySync(
                    "icmf_get_headers_with_messages_between_heights",
                    gtv(
                            mapOf(
                                    "topic" to gtv(topic),
                                    "from_anchor_height" to gtv(lastAnchorHeight.get() + 1),
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
                    return // TODO how to handle validation errors?
                }

                val icmfHeaderData = decodedHeader.getExtra()[ICMF_BLOCK_HEADER_EXTRA]
                if (icmfHeaderData == null) {
                    logger.warn("$ICMF_BLOCK_HEADER_EXTRA block header extra data missing for block-rid: $blockRid for blockchain-rid: ${decodedHeader.getBlockchainRid()} at height: ${decodedHeader.getHeight()}")
                    return // TODO how to handle validation errors?
                }

                val topicData = icmfHeaderData[topic]?.let { TopicHeaderData.fromGtv(it) }
                if (topicData == null) {
                    logger.warn("$ICMF_BLOCK_HEADER_EXTRA header extra data missing topic $topic for block-rid: $blockRid for blockchain-rid: ${decodedHeader.getBlockchainRid()} at height: ${decodedHeader.getHeight()}")
                    return // TODO how to handle validation errors?
                }

                /* TODO validatePrevMessageHeight
                if (!validatePrevMessageHeight(
                                bctx,
                                decodedHeader.getBlockchainRid(),
                                topic,
                                topicData,
                                decodedHeader.getHeight()
                        )
                ) return // TODO how to handle validation errors?
                 */

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
                    return
                }

                currentPackets.add(IcmfPacket(decodedHeader.getHeight(), blockRid, header.rawHeader, header.rawWitness,
                        bodies.map { IcmfMessage(BlockchainRid(decodedHeader.getBlockchainRid()), decodedHeader.getHeight(), topic, it) }))
            }
        }

        packets[currentAnchorHeight] = IcmfPackets(currentAnchorHeight, currentPackets)

        lastAnchorHeight.set(currentAnchorHeight)
    }

    override fun mightHaveNewPackets(): Boolean = packets.isNotEmpty()

    override fun fetchNext(currentPointer: Long): IcmfPackets<Long>? =
            packets.higherEntry(currentPointer)?.value

    override fun markTaken(currentPointer: Long, bctx: BlockEContext) {
        bctx.addAfterCommitHook {
            packets.remove(currentPointer)
        }
    }

    private fun fetchClusterInfoFromD1(clusterName: String): D1ClusterInfo = TODO("fetchClusterInfoFromD1")

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
    }
}

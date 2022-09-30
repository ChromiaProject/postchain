package net.postchain.d1.icmf

import mu.KLogging
import net.postchain.base.SpecialTransactionPosition
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.BlockEContext
import net.postchain.crypto.CryptoSystem
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.OpData
import net.postchain.gtx.special.GTXSpecialTxExtension

class IcmfReceiverSpecialTxExtension(private val dbOperations: IcmfDatabaseOperations) : GTXSpecialTxExtension {

    companion object : KLogging()

    private val _relevantOps = setOf(HeaderOp.OP_NAME, MessageOp.OP_NAME)
    private lateinit var cryptoSystem: CryptoSystem
    lateinit var receiver: GlobalTopicIcmfReceiver
    lateinit var clusterManagement: ClusterManagement

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
        val pipes = receiver.getRelevantPipes()

        val lastAnchoredHeights = dbOperations.loadLastAnchoredHeights(bctx).associate { (it.cluster to it.topic) to it.height }

        val allOps = mutableListOf<OpData>()
        for (pipe in pipes) {
            if (pipe.mightHaveNewPackets()) {
                val clusterName = pipe.clusterName
                val lastAnchoredHeight = lastAnchoredHeights[clusterName to pipe.route.topic] ?: -1
                var currentHeight: Long = lastAnchoredHeight
                while (pipe.mightHaveNewPackets()) {
                    val icmfPackets = pipe.fetchNext(currentHeight)
                    if (icmfPackets != null) {
                        for (packet in icmfPackets.packets) {
                            val currentPrevMessageBlockHeight = dbOperations.loadLastMessageHeight(bctx, packet.sender, packet.topic)
                            if (packet.height > currentPrevMessageBlockHeight) {
                                allOps.addAll(buildOpData(packet))
                            }
                            // else already processed in previous block, so skip it here
                        }
                        pipe.markTaken(icmfPackets.currentPointer, bctx)
                        currentHeight = icmfPackets.currentPointer
                    } else {
                        break // Nothing more to find
                    }
                }
                if (currentHeight > lastAnchoredHeight) {
                    dbOperations.saveLastAnchoredHeight(bctx, clusterName, pipe.route.topic, currentHeight)
                }
            }
        }
        return allOps
    }

    private fun buildOpData(icmfPacket: IcmfPacket): List<OpData> {
        val operations = mutableListOf<OpData>()
        operations.add(HeaderOp(icmfPacket.rawHeader, icmfPacket.rawWitness).toOpData())

        for (body in icmfPacket.bodies) {
            operations.add(MessageOp(icmfPacket.sender, icmfPacket.topic, body).toOpData())
        }
        return operations
    }

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
                HeaderOp.OP_NAME -> {
                    val headerOp = HeaderOp.fromOpData(op) ?: return false

                    if (!validateMessages(messageHashes, currentHeaderData, bctx)) return false
                    messageHashes.clear()

                    val decodedHeader = BlockHeaderData.fromBinary(headerOp.rawHeader)
                    val blockRid = decodedHeader.toGtv().merkleHash(GtvMerkleHashCalculator(cryptoSystem))
                    val topicData = TopicHeaderData.extractTopicHeaderData(decodedHeader, headerOp.rawHeader, headerOp.rawWitness, blockRid, cryptoSystem, clusterManagement)
                            ?: return false

                    currentHeaderData = HeaderValidationInfo(
                            decodedHeader.getHeight(),
                            decodedHeader.getBlockchainRid(),
                            topicData
                    )
                }

                MessageOp.OP_NAME -> {
                    val messageOp = MessageOp.fromOpData(op) ?: return false

                    if (currentHeaderData == null) {
                        logger.warn("got ${MessageOp.OP_NAME} before any ${HeaderOp.OP_NAME}")
                        return false
                    }

                    val topicData = currentHeaderData.icmfHeaderData[messageOp.topic]
                    if (topicData == null) {
                        logger.warn("$ICMF_BLOCK_HEADER_EXTRA header extra data missing topic $messageOp.topic for sender ${messageOp.sender.toHex()}")
                        return false
                    }

                    messageHashes.computeIfAbsent(messageOp.topic) { mutableListOf() }
                            .add(cryptoSystem.digest(GtvEncoder.encodeGtv(messageOp.body)))
                }

                else -> {
                    logger.warn("Got unexpected special operation: ${op.opName}")
                    return false
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
                                data.prevMessageBlockHeight,
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
            prevMessageBlockHeight: Long,
            height: Long
    ): Boolean {
        val currentPrevMessageBlockHeight = dbOperations.loadLastMessageHeight(bctx, BlockchainRid(sender), topic)

        if (prevMessageBlockHeight != currentPrevMessageBlockHeight) {
            logger.warn("$ICMF_BLOCK_HEADER_EXTRA header extra has incorrect previous message height $prevMessageBlockHeight, expected $currentPrevMessageBlockHeight for topic $topic for sender ${sender.toHex()}")
            return false
        }

        dbOperations.saveLastMessageHeight(bctx, BlockchainRid(sender), topic, height)

        return true
    }

    private fun validateMessagesHash(
            messageHashes: MutableMap<String, MutableList<ByteArray>>,
            headerData: HeaderValidationInfo?
    ): Boolean {
        for ((topic, hashes) in messageHashes) {
            if (headerData == null) {
                logger.error("got ${MessageOp.OP_NAME} before any ${HeaderOp.OP_NAME}")
                return false
            }

            val topicData = headerData.icmfHeaderData[topic]
            if (topicData == null) {
                logger.error("$ICMF_BLOCK_HEADER_EXTRA header extra data missing topic $topic")
                return false
            }

            val computedHash = TopicHeaderData.calculateMessagesHash(hashes, cryptoSystem)
            if (!topicData.hash.contentEquals(computedHash)) {
                logger.warn("invalid messages hash for topic: $topic")
                return false
            }
        }
        return true
    }

    data class HeaderValidationInfo(
            val height: Long,
            val sender: ByteArray,
            val icmfHeaderData: Map<String, TopicHeaderData>
    )

    data class HeaderOp(
            val rawHeader: ByteArray,
            val rawWitness: ByteArray
    ) {
        companion object {
            // operation __icmf_header(block_header: byte_array, witness: byte_array)
            const val OP_NAME = "__icmf_header"

            fun fromOpData(opData: OpData): HeaderOp? {
                if (opData.opName != OP_NAME) return null
                if (opData.args.size != 2) {
                    logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                    return null
                }

                return try {
                    HeaderOp(opData.args[0].asByteArray(), opData.args[1].asByteArray())
                } catch (e: UserMistake) {
                    logger.warn("Got $OP_NAME operation with invalid argument types: ${e.message}")
                    null
                }
            }
        }

        fun toOpData() = OpData(OP_NAME, arrayOf(gtv(rawHeader), gtv(rawWitness)))
    }

    data class MessageOp(
            val sender: BlockchainRid,
            val topic: String,
            val body: Gtv
    ) {
        companion object {
            // operation __icmf_message(sender: byte_array, topic: text, body: gtv)
            const val OP_NAME = "__icmf_message"

            fun fromOpData(opData: OpData): MessageOp? {
                if (opData.opName != OP_NAME) return null
                if (opData.args.size != 3) {
                    logger.warn("Got $OP_NAME operation with wrong number of arguments: ${opData.args.size}")
                    return null
                }

                return try {
                    MessageOp(BlockchainRid(opData.args[0].asByteArray()), opData.args[1].asString(), opData.args[2])
                } catch (e: UserMistake) {
                    logger.warn("Got $OP_NAME operation with invalid argument types: ${e.message}")
                    null
                }
            }
        }

        fun toOpData() = OpData(OP_NAME, arrayOf(gtv(sender), gtv(topic), body))
    }
}

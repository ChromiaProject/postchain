package net.postchain.d1.icmf

import mu.KLogging
import net.postchain.base.BaseBlockWitness
import net.postchain.base.SpecialTransactionPosition
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.core.BlockEContext
import net.postchain.crypto.CryptoSystem
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.OpData
import net.postchain.gtx.special.GTXSpecialTxExtension

class IcmfRemoteSpecialTxExtension : GTXSpecialTxExtension {

    companion object : KLogging() {
        // operation __icmf_header(block_header: byte_array, witness: byte_array)
        const val OP_ICMF_HEADER = "__icmf_header"

        // operation __icmf_message(sender: byte_array, topic: text, body: gtv)
        const val OP_ICMF_MESSAGE = "__icmf_message"
    }

    private val _relevantOps = setOf(OP_ICMF_HEADER, OP_ICMF_MESSAGE)
    private lateinit var cryptoSystem: CryptoSystem
    lateinit var receiver: GlobalTopicIcmfReceiver

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

        val allOps = mutableListOf<OpData>()
        for (pipe in pipes) {
            if (pipe.mightHaveNewPackets()) {
                val clusterName = pipe.id
                val lastAnchoredHeight = IcmfDatabaseOperations.loadLastAnchoredHeight(bctx, clusterName)
                var currentHeight: Long = lastAnchoredHeight
                while (pipe.mightHaveNewPackets()) {
                    val icmfPackets = pipe.fetchNext(currentHeight)
                    if (icmfPackets != null) {
                        for (packet in icmfPackets.packets) {
                            val currentPrevMessageBlockHeight = IcmfDatabaseOperations.loadLastMessageHeight(bctx, packet.sender, packet.topic)
                            if (packet.height > currentPrevMessageBlockHeight) {
                                IcmfDatabaseOperations.saveLastMessageHeight(bctx, packet.sender, packet.topic, packet.height)
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
                    IcmfDatabaseOperations.saveLastAnchoredHeight(bctx, clusterName, currentHeight)
                }
            }
        }
        return allOps
    }

    private fun buildOpData(icmfPacket: IcmfPacket): List<OpData> {
        val operations = mutableListOf<OpData>()
        operations.add(OpData(OP_ICMF_HEADER, arrayOf(gtv(icmfPacket.rawHeader), gtv(icmfPacket.rawWitness))))

        for (body in icmfPacket.bodies) {
            operations.add(OpData(OP_ICMF_MESSAGE, arrayOf(gtv(icmfPacket.sender), gtv(icmfPacket.topic), body)))
        }
        return operations
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

                    val peers = fetchChainInfoFromD1(BlockchainRid(decodedHeader.getBlockchainRid()), decodedHeader.getHeight())

                    if (!Validation.validateBlockSignatures(cryptoSystem, decodedHeader.getPreviousBlockRid(), rawHeader, blockRid, peers.map { it.pubKey }, witness)) {
                        logger.warn("Invalid block header signature for block-rid: $blockRid for blockchain-rid: ${decodedHeader.getBlockchainRid()} at height: ${decodedHeader.getHeight()}")
                        return false
                    }

                    val icmfHeaderData = decodedHeader.getExtra()[ICMF_BLOCK_HEADER_EXTRA]
                    if (icmfHeaderData == null) {
                        logger.warn("$ICMF_BLOCK_HEADER_EXTRA block header extra data missing for block-rid: $blockRid for blockchain-rid: ${decodedHeader.getBlockchainRid()} at height: ${decodedHeader.getHeight()}")
                        return false
                    }

                    currentHeaderData = HeaderValidationInfo(
                            decodedHeader.getHeight(),
                            decodedHeader.getBlockchainRid(),
                            icmfHeaderData.asDict().mapValues { TopicHeaderData.fromGtv(it.value) })
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
        val currentPrevMessageBlockHeight = IcmfDatabaseOperations.loadLastMessageHeight(bctx, BlockchainRid(sender), topic)

        if (prevMessageBlockHeight != currentPrevMessageBlockHeight) {
            logger.warn("$ICMF_BLOCK_HEADER_EXTRA header extra has incorrect previous message height $prevMessageBlockHeight, expected $prevMessageBlockHeight for topic $topic for sender ${sender.toHex()}")
            return false
        }

        IcmfDatabaseOperations.saveLastMessageHeight(bctx, BlockchainRid(sender), topic, height)

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

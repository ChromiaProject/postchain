package net.postchain.d1.icmf

import net.postchain.base.BaseBlockWitness
import net.postchain.base.SpecialTransactionPosition
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockEContext
import net.postchain.core.BlockRid
import net.postchain.crypto.PrivKey
import net.postchain.crypto.PubKey
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.secp256k1_derivePubKey
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.OpData
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class IcmfValidationTest {
    private val topic = "my-topic"
    private val blockchainRID = BlockchainRid.buildRepeat(1)
    private val cryptoSystem = Secp256K1CryptoSystem()
    private val chainID: Long = 1

    private val mockModule: GTXModule = mock {}
    private val mockContext: BlockEContext = mock {}
    private val dbMock: IcmfDatabaseOperations = mock {
        on { loadLastMessageHeight(mockContext, blockchainRID, topic) } doReturn -1L
    }

    @Test
    fun success() {
        val icmfRemoteSpecialTxExtension = IcmfRemoteSpecialTxExtension(dbMock)
        icmfRemoteSpecialTxExtension.init(mockModule, chainID, blockchainRID, cryptoSystem)
        icmfRemoteSpecialTxExtension.clusterManagement = IcmfTestClusterManagement()

        val messageBody = gtv("hej")
        val encodedMessageBody = GtvEncoder.encodeGtv(messageBody)

        val header = makeBlockHeader(blockchainRID, BlockRid(blockchainRID.data), 0, mapOf(
                ICMF_BLOCK_HEADER_EXTRA to gtv(mapOf(
                        topic to TopicHeaderData(cryptoSystem.digest(cryptoSystem.digest(encodedMessageBody)), -1).toGtv()
                ))
        ))
        val blockRid = header.toGtv().merkleHash(GtvMerkleHashCalculator(cryptoSystem))
        val rawWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(IcmfTestClusterManagement.pubKey.key, IcmfTestClusterManagement.privKey.key).signDigest(blockRid))
        ).getRawData()
        val headerOp = IcmfRemoteSpecialTxExtension.HeaderOp(GtvEncoder.encodeGtv(header.toGtv()), rawWitness).toOpData()

        val messageOp = IcmfRemoteSpecialTxExtension.MessageOp(blockchainRID, topic, messageBody).toOpData()

        assertTrue(icmfRemoteSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(headerOp, messageOp)))
    }

    @Test
    fun invalidParameters() {
        val icmfRemoteSpecialTxExtension = IcmfRemoteSpecialTxExtension(dbMock)
        icmfRemoteSpecialTxExtension.init(mockModule, chainID, blockchainRID, cryptoSystem)
        icmfRemoteSpecialTxExtension.clusterManagement = IcmfTestClusterManagement()

        val headerOp = OpData(IcmfRemoteSpecialTxExtension.HeaderOp.OP_NAME, arrayOf(
                GtvNull,
                GtvNull
        ))

        assertFalse(icmfRemoteSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(headerOp)))
    }

    @Test
    fun invalidOperationOrder() {
        val icmfRemoteSpecialTxExtension = IcmfRemoteSpecialTxExtension(dbMock)
        icmfRemoteSpecialTxExtension.init(mockModule, chainID, blockchainRID, cryptoSystem)
        icmfRemoteSpecialTxExtension.clusterManagement = IcmfTestClusterManagement()

        val messageBody = gtv("hej")
        val encodedMessageBody = GtvEncoder.encodeGtv(messageBody)

        val header = makeBlockHeader(blockchainRID, BlockRid(blockchainRID.data), 0, mapOf(
                ICMF_BLOCK_HEADER_EXTRA to gtv(mapOf(
                        topic to TopicHeaderData(cryptoSystem.digest(cryptoSystem.digest(encodedMessageBody)), -1).toGtv()
                ))
        ))
        val blockRid = header.toGtv().merkleHash(GtvMerkleHashCalculator(cryptoSystem))
        val rawWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(IcmfTestClusterManagement.pubKey.key, IcmfTestClusterManagement.privKey.key).signDigest(blockRid))
        ).getRawData()
        val headerOp = IcmfRemoteSpecialTxExtension.HeaderOp(GtvEncoder.encodeGtv(header.toGtv()), rawWitness).toOpData()

        val messageOp = IcmfRemoteSpecialTxExtension.MessageOp(blockchainRID, topic, messageBody).toOpData()

        assertFalse(icmfRemoteSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(messageOp, headerOp)))
    }

    @Test
    fun invalidSignature() {
        val icmfRemoteSpecialTxExtension = IcmfRemoteSpecialTxExtension(dbMock)
        icmfRemoteSpecialTxExtension.init(mockModule, chainID, blockchainRID, cryptoSystem)
        icmfRemoteSpecialTxExtension.clusterManagement = IcmfTestClusterManagement()

        val header = makeBlockHeader(blockchainRID, BlockRid(blockchainRID.data), 0, mapOf(
                ICMF_BLOCK_HEADER_EXTRA to gtv(mapOf())
        ))

        val blockRid = header.toGtv().merkleHash(GtvMerkleHashCalculator(cryptoSystem))
        val invalidSignerPrivKey = PrivKey(cryptoSystem.getRandomBytes(32))
        val invalidSignerPubKey = PubKey(secp256k1_derivePubKey(invalidSignerPrivKey.key))
        val rawWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(invalidSignerPubKey.key, invalidSignerPrivKey.key).signDigest(blockRid))
        ).getRawData()
        val headerOp = IcmfRemoteSpecialTxExtension.HeaderOp(GtvEncoder.encodeGtv(header.toGtv()), rawWitness).toOpData()

        assertFalse(icmfRemoteSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(headerOp)))
    }

    @Test
    fun missingExtraHeader() {
        val icmfRemoteSpecialTxExtension = IcmfRemoteSpecialTxExtension(dbMock)
        icmfRemoteSpecialTxExtension.init(mockModule, chainID, blockchainRID, cryptoSystem)
        icmfRemoteSpecialTxExtension.clusterManagement = IcmfTestClusterManagement()

        val header = makeBlockHeader(blockchainRID, BlockRid(blockchainRID.data), 0, mapOf())

        val blockRid = header.toGtv().merkleHash(GtvMerkleHashCalculator(cryptoSystem))
        val rawWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(IcmfTestClusterManagement.pubKey.key, IcmfTestClusterManagement.privKey.key).signDigest(blockRid))
        ).getRawData()
        val headerOp = IcmfRemoteSpecialTxExtension.HeaderOp(GtvEncoder.encodeGtv(header.toGtv()), rawWitness).toOpData()

        assertFalse(icmfRemoteSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(headerOp)))
    }

    @Test
    fun missingExtraHeaderTopicData() {
        val icmfRemoteSpecialTxExtension = IcmfRemoteSpecialTxExtension(dbMock)
        icmfRemoteSpecialTxExtension.init(mockModule, chainID, blockchainRID, cryptoSystem)
        icmfRemoteSpecialTxExtension.clusterManagement = IcmfTestClusterManagement()

        val messageBody = gtv("hej")

        val header = makeBlockHeader(blockchainRID, BlockRid(blockchainRID.data), 0, mapOf(
                ICMF_BLOCK_HEADER_EXTRA to gtv(mapOf())
        ))
        val blockRid = header.toGtv().merkleHash(GtvMerkleHashCalculator(cryptoSystem))
        val rawWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(IcmfTestClusterManagement.pubKey.key, IcmfTestClusterManagement.privKey.key).signDigest(blockRid))
        ).getRawData()
        val headerOp = IcmfRemoteSpecialTxExtension.HeaderOp(GtvEncoder.encodeGtv(header.toGtv()), rawWitness).toOpData()

        val messageOp = IcmfRemoteSpecialTxExtension.MessageOp(blockchainRID, topic, messageBody).toOpData()

        assertFalse(icmfRemoteSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(headerOp, messageOp)))
    }

    @Test
    fun incorrectMessageBody() {
        val icmfRemoteSpecialTxExtension = IcmfRemoteSpecialTxExtension(dbMock)
        icmfRemoteSpecialTxExtension.init(mockModule, chainID, blockchainRID, cryptoSystem)
        icmfRemoteSpecialTxExtension.clusterManagement = IcmfTestClusterManagement()

        val messageBody = gtv("hej")
        val encodedMessageBody = GtvEncoder.encodeGtv(messageBody)
        val incorrectMessageBody = gtv("nej")

        val header = makeBlockHeader(blockchainRID, BlockRid(blockchainRID.data), 0, mapOf(
                ICMF_BLOCK_HEADER_EXTRA to gtv(mapOf(
                        topic to TopicHeaderData(cryptoSystem.digest(cryptoSystem.digest(encodedMessageBody)), -1).toGtv()
                ))
        ))
        val blockRid = header.toGtv().merkleHash(GtvMerkleHashCalculator(cryptoSystem))
        val rawWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(IcmfTestClusterManagement.pubKey.key, IcmfTestClusterManagement.privKey.key).signDigest(blockRid))
        ).getRawData()
        val headerOp = IcmfRemoteSpecialTxExtension.HeaderOp(GtvEncoder.encodeGtv(header.toGtv()), rawWitness).toOpData()

        val messageOp = IcmfRemoteSpecialTxExtension.MessageOp(blockchainRID, topic, incorrectMessageBody).toOpData()

        assertFalse(icmfRemoteSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(headerOp, messageOp)))
    }

    @Test
    fun skippingMessage() {
        val icmfRemoteSpecialTxExtension = IcmfRemoteSpecialTxExtension(dbMock)
        icmfRemoteSpecialTxExtension.init(mockModule, chainID, blockchainRID, cryptoSystem)
        icmfRemoteSpecialTxExtension.clusterManagement = IcmfTestClusterManagement()

        val messageBody0 = gtv("hej0")
        val encodedMessageBody0 = GtvEncoder.encodeGtv(messageBody0)
        val messageBody1 = gtv("hej1")
        val encodedMessageBody1 = GtvEncoder.encodeGtv(messageBody1)

        val header = makeBlockHeader(blockchainRID, BlockRid(blockchainRID.data), 0, mapOf(
                ICMF_BLOCK_HEADER_EXTRA to gtv(mapOf(
                        topic to TopicHeaderData(
                                cryptoSystem.digest(cryptoSystem.digest(encodedMessageBody0).plus(cryptoSystem.digest(encodedMessageBody1))),
                                -1
                        ).toGtv()
                ))
        ))
        val blockRid = header.toGtv().merkleHash(GtvMerkleHashCalculator(cryptoSystem))
        val rawWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(IcmfTestClusterManagement.pubKey.key, IcmfTestClusterManagement.privKey.key).signDigest(blockRid))
        ).getRawData()
        val headerOp = IcmfRemoteSpecialTxExtension.HeaderOp(GtvEncoder.encodeGtv(header.toGtv()), rawWitness).toOpData()

        val messageOp = IcmfRemoteSpecialTxExtension.MessageOp(blockchainRID, topic, messageBody0).toOpData()

        assertFalse(icmfRemoteSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(headerOp, messageOp)))
    }

    @Test
    fun injectMessage() {
        val icmfRemoteSpecialTxExtension = IcmfRemoteSpecialTxExtension(dbMock)
        icmfRemoteSpecialTxExtension.init(mockModule, chainID, blockchainRID, cryptoSystem)
        icmfRemoteSpecialTxExtension.clusterManagement = IcmfTestClusterManagement()

        val messageBody = gtv("hej")
        val encodedMessageBody = GtvEncoder.encodeGtv(messageBody)
        val injectedMessageBody = gtv("hej2")

        val header = makeBlockHeader(blockchainRID, BlockRid(blockchainRID.data), 0, mapOf(
                ICMF_BLOCK_HEADER_EXTRA to gtv(mapOf(
                        topic to TopicHeaderData(cryptoSystem.digest(cryptoSystem.digest(encodedMessageBody)), -1).toGtv()
                ))
        ))
        val blockRid = header.toGtv().merkleHash(GtvMerkleHashCalculator(cryptoSystem))
        val rawWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(IcmfTestClusterManagement.pubKey.key, IcmfTestClusterManagement.privKey.key).signDigest(blockRid))
        ).getRawData()
        val headerOp = IcmfRemoteSpecialTxExtension.HeaderOp(GtvEncoder.encodeGtv(header.toGtv()), rawWitness).toOpData()

        val messageOp0 = IcmfRemoteSpecialTxExtension.MessageOp(blockchainRID, topic, messageBody).toOpData()
        val messageOp1 = IcmfRemoteSpecialTxExtension.MessageOp(blockchainRID, topic, injectedMessageBody).toOpData()

        assertFalse(icmfRemoteSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(headerOp, messageOp0, messageOp1)))
    }

    @Test
    fun invalidPreviousMessageHeight() {
        val icmfRemoteSpecialTxExtension = IcmfRemoteSpecialTxExtension(dbMock)
        icmfRemoteSpecialTxExtension.init(mockModule, chainID, blockchainRID, cryptoSystem)
        icmfRemoteSpecialTxExtension.clusterManagement = IcmfTestClusterManagement()

        val messageBody = gtv("hej")
        val encodedMessageBody = GtvEncoder.encodeGtv(messageBody)

        // Header data indicates that primary is trying to skip messages from block 0
        val header = makeBlockHeader(blockchainRID, BlockRid(blockchainRID.data), 1, mapOf(
                ICMF_BLOCK_HEADER_EXTRA to gtv(mapOf(
                        topic to TopicHeaderData(cryptoSystem.digest(cryptoSystem.digest(encodedMessageBody)), 0).toGtv()
                ))
        ))
        val blockRid = header.toGtv().merkleHash(GtvMerkleHashCalculator(cryptoSystem))
        val rawWitness = BaseBlockWitness.fromSignatures(
                arrayOf(cryptoSystem.buildSigMaker(IcmfTestClusterManagement.pubKey.key, IcmfTestClusterManagement.privKey.key).signDigest(blockRid))
        ).getRawData()
        val headerOp = IcmfRemoteSpecialTxExtension.HeaderOp(GtvEncoder.encodeGtv(header.toGtv()), rawWitness).toOpData()

        val messageOp = IcmfRemoteSpecialTxExtension.MessageOp(blockchainRID, topic, messageBody).toOpData()

        assertFalse(icmfRemoteSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(headerOp, messageOp)))
    }

    private fun makeBlockHeader(blockchainRID: BlockchainRid, previousBlockRid: BlockRid, height: Long, extra: Map<String, Gtv>) = BlockHeaderData(
            gtvBlockchainRid = gtv(blockchainRID),
            gtvPreviousBlockRid = gtv(previousBlockRid.data),
            gtvMerkleRootHash = gtv(ByteArray(32)),
            gtvTimestamp = gtv(height),
            gtvHeight = gtv(height),
            gtvDependencies = GtvNull,
            gtvExtra = gtv(extra)
    )
}
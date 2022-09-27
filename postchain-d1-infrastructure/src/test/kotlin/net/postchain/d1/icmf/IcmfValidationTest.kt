package net.postchain.d1.icmf

import net.postchain.base.BaseBlockWitness
import net.postchain.base.SpecialTransactionPosition
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockEContext
import net.postchain.core.BlockRid
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.OpData
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
                arrayOf(cryptoSystem.buildSigMaker(IcmfTestClusterManagement.pubKey.key, IcmfTestClusterManagement.privKey.key).signDigest(blockRid))).getRawData()
        val headerOp = OpData(IcmfRemoteSpecialTxExtension.OP_ICMF_HEADER, arrayOf(
                gtv(GtvEncoder.encodeGtv(header.toGtv())),
                gtv(rawWitness)
        ))

        assertTrue(icmfRemoteSpecialTxExtension.validateSpecialOperations(SpecialTransactionPosition.Begin, mockContext, listOf(headerOp)))
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
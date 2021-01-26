package net.postchain.devtools.l2

import net.postchain.base.BlockchainRid
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.gtv.BlockHeaderDataFactory
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.core.Transaction
import net.postchain.crypto.SECP256K1Keccak
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.KeyPairHelper
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.gtx.myCS
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvNull
import net.postchain.gtx.GTXDataBuilder
import org.junit.Assert
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class L2BlockBuilderTest : IntegrationTestSetup() {

    fun makeL2EventOp(bcRid: BlockchainRid, num: Long): ByteArray {
        val b = GTXDataBuilder(bcRid, arrayOf(KeyPairHelper.pubKey(0)), myCS)
        b.addOperation("l2_event",
            arrayOf(
                GtvFactory.gtv(num),
                GtvFactory.gtv(SECP256K1Keccak.digest(BigInteger.valueOf(num).toByteArray()))
            )
        )
        b.finish()
        b.sign(myCS.buildSigMaker(KeyPairHelper.pubKey(0), KeyPairHelper.privKey(0)))
        return b.serialize()
    }

    fun makeL2StateOp(bcRid: BlockchainRid, num: Long): ByteArray {
        val b = GTXDataBuilder(bcRid, arrayOf(KeyPairHelper.pubKey(0)), myCS)
        b.addOperation("l2_state",
            arrayOf(
                GtvFactory.gtv(num),
                GtvFactory.gtv(num),
                GtvFactory.gtv(SECP256K1Keccak.digest(BigInteger.valueOf(num).toByteArray()))
            )
        )
        b.finish()
        b.sign(myCS.buildSigMaker(KeyPairHelper.pubKey(0), KeyPairHelper.privKey(0)))
        return b.serialize()
    }

    fun makeNOPGTX(bcRid: BlockchainRid): ByteArray {
        val b = GTXDataBuilder(bcRid, arrayOf(KeyPairHelper.pubKey(0)), myCS)
        b.addOperation("nop", arrayOf(GtvFactory.gtv(42)))
        b.finish()
        b.sign(myCS.buildSigMaker(KeyPairHelper.pubKey(0), KeyPairHelper.privKey(0)))
        return b.serialize()
    }

    fun makeTestTx(id: Long, value: String, bcRid: BlockchainRid): ByteArray {
        val b = GTXDataBuilder(bcRid, arrayOf(KeyPairHelper.pubKey(0)), myCS)
        b.addOperation("gtx_test", arrayOf(GtvFactory.gtv(id), GtvFactory.gtv(value)))
        b.finish()
        b.sign(myCS.buildSigMaker(KeyPairHelper.pubKey(0), KeyPairHelper.privKey(0)))
        return b.serialize()
    }

    fun makeTimeBTx(from: Long, to: Long?, bcRid: BlockchainRid): ByteArray {
        val b = GTXDataBuilder(bcRid, arrayOf(KeyPairHelper.pubKey(0)), myCS)
        b.addOperation("timeb", arrayOf(
            GtvFactory.gtv(from),
            if (to != null) GtvFactory.gtv(to) else GtvNull
        ))
        // Need to add a valid dummy operation to make the entire TX valid
        b.addOperation("gtx_test", arrayOf(GtvFactory.gtv(1), GtvFactory.gtv("true")))
        b.finish()
        b.sign(myCS.buildSigMaker(KeyPairHelper.pubKey(0), KeyPairHelper.privKey(0)))
        return b.serialize()
    }

    @Test
    fun testL2BuildBlock() {
        configOverrides.setProperty("infrastructure", "base/test")
        val nodes = createNodes(1, "/net/postchain/devtools/l2/blockchain_config.xml")
        val node = nodes[0]
        val bcRid = systemSetup.blockchainMap[1]!!.rid // Just assume we have chain 1

        fun enqueueTx(data: ByteArray): Transaction? {
            try {
                val tx = node.getBlockchainInstance().getEngine().getConfiguration().getTransactionFactory().decodeTransaction(data)
                node.getBlockchainInstance().getEngine().getTransactionQueue().enqueue(tx)
                return tx
            } catch (e: Exception) {
                logger.error(e) { "Can't enqueue tx" }
            }
            return null
        }

        val validTxs = mutableListOf<Transaction>()
        var currentBlockHeight = -1L

        fun makeSureBlockIsBuiltCorrectly() {
            currentBlockHeight += 1
            buildBlockAndCommit(node.getBlockchainInstance().getEngine())
            Assert.assertEquals(currentBlockHeight, getBestHeight(node))
            val ridsAtHeight = getTxRidsAtHeight(node, currentBlockHeight)
            for (vtx in validTxs) {
                val vtxRID = vtx.getRID()
                Assert.assertTrue(ridsAtHeight.any { it.contentEquals(vtxRID) })
            }
            Assert.assertEquals(validTxs.size, ridsAtHeight.size)
            validTxs.clear()
        }

        // Tx1 valid)
        val validTx1 = enqueueTx(makeTestTx(1, "true", bcRid))!!
        validTxs.add(validTx1)

        // Tx 2 invalid, b/c bad args
        enqueueTx(makeTestTx(2, "false", bcRid))!!

        // Tx 3: Nop (invalid, since need more ops)
        val x = makeNOPGTX(bcRid)
        enqueueTx(x)

        // Tx: L2 Event Op
        val validTx2 = enqueueTx(makeL2EventOp(bcRid, 1L))!!
        validTxs.add(validTx2)

        // Tx: L2 Account State Op
        val validTx3 = enqueueTx(makeL2StateOp(bcRid, 2L))!!
        validTxs.add(validTx3)

        // -------------------------
        // Build it
        // -------------------------
        makeSureBlockIsBuiltCorrectly()

        val blockHeaderData = getBlockHeaderData(node, currentBlockHeight)
        val extraData = blockHeaderData.gtvExtra
        val l2RootHash = extraData["l2RootHash"]?.asByteArray()
        val eventData = "0000000000000000000000000000000000000000000000000000000000000002f2ee15ea639b73fa3db9b34a245bdfa015c260c598b211bf05a1ecc4b3e3b4f2".hexStringToByteArray()
        val eventHash = SECP256K1Keccak.digest(eventData)
        val stateData = "00000000000000000000000000000000000000000000000000000000000000015fe7f977e71dba2ea1a68e21057beebb9be2ac30c6410aa38d4f3fbe41dcffd2".hexStringToByteArray()
        val stateHash = SECP256K1Keccak.digest(stateData)
        val root = eventHash.plus(stateHash)
        assertEquals(l2RootHash!!.toHex(), root.toHex())

        // Tx 4: time, valid, no stop is ok
        val tx4Time = makeTimeBTx(0, null, bcRid)
        validTxs.add(enqueueTx(tx4Time)!!)

        // Tx 5: time, valid, from beginning of time to now
        val tx5Time = makeTimeBTx(0, System.currentTimeMillis(), bcRid)
        validTxs.add(enqueueTx(tx5Time)!!)

        // TX 6: time, invalid since from bigger than to
        val tx6Time = makeTimeBTx(100, 0, bcRid)
        enqueueTx(tx6Time)

        // TX 7: time, invalid since from is in the future
        val tx7Time = makeTimeBTx(System.currentTimeMillis() + 100, null, bcRid)
        enqueueTx(tx7Time)

        // -------------------------
        // Build it
        // -------------------------
        makeSureBlockIsBuiltCorrectly()

        val value = node.getBlockchainInstance().getEngine().getBlockQueries().query(
            """{"type"="gtx_test_get_value", "txRID"="${validTx1.getRID().toHex()}"}""")
        Assert.assertEquals("\"true\"", value.get())
    }

    private fun getBlockHeaderData(node: PostchainTestNode, height: Long): BlockHeaderData {
        val blockQueries = node.getBlockchainInstance().getEngine().getBlockQueries()
        val blockRid = blockQueries.getBlockRid(height).get()
        val blockHeader = blockQueries.getBlockHeader(blockRid!!).get()
        return BlockHeaderDataFactory.buildFromBinary(blockHeader.rawData)
    }
}
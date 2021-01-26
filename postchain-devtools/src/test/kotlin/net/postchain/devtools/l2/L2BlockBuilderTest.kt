package net.postchain.devtools.l2

import net.postchain.base.BlockchainRid
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.gtv.BlockHeaderDataFactory
import net.postchain.common.data.Hash
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.core.Transaction
import net.postchain.crypto.SECP256K1Keccak
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.KeyPairHelper
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.gtx.myCS
import net.postchain.gtv.*
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

    @Test
    fun testL2UpdateSnapshot() {
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

        var currentBlockHeight = -1L

        fun sealBlock() {
            currentBlockHeight += 1
            buildBlockAndCommit(node.getBlockchainInstance().getEngine())
            Assert.assertEquals(currentBlockHeight, getBestHeight(node))
        }

        // enqueue txs that emit accounts' state
        val leafHashes = mutableMapOf<Long, Hash>()
        for (i in 0..15) {
            enqueueTx(makeL2StateOp(bcRid, i.toLong()))
            val l = i.toLong()
            val state = GtvEncoder.simpleEncodeGtv(GtvFactory.gtv(
                GtvInteger(l),
                GtvByteArray(SECP256K1Keccak.digest(BigInteger.valueOf(l).toByteArray()))))
            leafHashes[l] = SECP256K1Keccak.digest(state)
        }

        // build 1st block and commit
        sealBlock()

        // calculate state root
        val l01 = SECP256K1Keccak.treeHasher(leafHashes[0]!!, leafHashes[1]!!)
        val l23 = SECP256K1Keccak.treeHasher(leafHashes[2]!!, leafHashes[3]!!)
        val hash00 = SECP256K1Keccak.treeHasher(l01, l23)
        val l45 = SECP256K1Keccak.treeHasher(leafHashes[4]!!, leafHashes[5]!!)
        val l67 = SECP256K1Keccak.treeHasher(leafHashes[6]!!, leafHashes[7]!!)
        val hash01 = SECP256K1Keccak.treeHasher(l45, l67)
        val l89 = SECP256K1Keccak.treeHasher(leafHashes[8]!!, leafHashes[9]!!)
        val l1011 = SECP256K1Keccak.treeHasher(leafHashes[10]!!, leafHashes[11]!!)
        val hash10 = SECP256K1Keccak.treeHasher(l89, l1011)
        val l1213 = SECP256K1Keccak.treeHasher(leafHashes[12]!!, leafHashes[13]!!)
        val l1415 = SECP256K1Keccak.treeHasher(leafHashes[14]!!, leafHashes[15]!!)
        val hash11 = SECP256K1Keccak.treeHasher(l1213, l1415)
        val leftHash = SECP256K1Keccak.treeHasher(hash00, hash01)
        val rightHash = SECP256K1Keccak.treeHasher(hash10, hash11)
        val root = SECP256K1Keccak.treeHasher(leftHash, rightHash)

        // query state root from block header's extra data
        val blockHeaderData = getBlockHeaderData(node, currentBlockHeight)
        val extraData = blockHeaderData.gtvExtra
        val l2RootHash = extraData["l2RootHash"]?.asByteArray()
        val l2StateRoot = l2RootHash!!.take(32).toByteArray()

        assertEquals(root.toHex(), l2StateRoot.toHex())

        val l = 16L
        enqueueTx(makeL2StateOp(bcRid, l))
        val state = GtvEncoder.simpleEncodeGtv(GtvFactory.gtv(
            GtvInteger(l),
            GtvByteArray(SECP256K1Keccak.digest(BigInteger.valueOf(l).toByteArray()))))
        leafHashes[l] = SECP256K1Keccak.digest(state)

        // build 2nd block and commit
        sealBlock()

        // query state root from block header's extra data
        val blockHeaderData2 = getBlockHeaderData(node, currentBlockHeight)
        val extraData2 = blockHeaderData2.gtvExtra
        val l2RootHash2 = extraData2["l2RootHash"]?.asByteArray()
        val l2StateRoot2 = l2RootHash2!!.take(32).toByteArray()

        // calculate state root
        val root2 = SECP256K1Keccak.treeHasher(root, leafHashes[l]!!)
        assertEquals(root2.toHex(), l2StateRoot2.toHex())
    }

    @Test
    fun testL2EventAndUpdateSnapshot() {
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

        var currentBlockHeight = -1L

        fun sealBlock() {
            currentBlockHeight += 1
            buildBlockAndCommit(node.getBlockchainInstance().getEngine())
            Assert.assertEquals(currentBlockHeight, getBestHeight(node))
        }

        // enqueue txs that emit event
        val leafs = mutableListOf<Hash>()
        for (i in 1..4) {
            val l = i.toLong()
            enqueueTx(makeL2EventOp(bcRid, l))
            val event = GtvEncoder.simpleEncodeGtv(GtvFactory.gtv(
                GtvInteger(l),
                GtvByteArray(SECP256K1Keccak.digest(BigInteger.valueOf(l).toByteArray())))
            )
            leafs.add(SECP256K1Keccak.digest(event))
        }

        // enqueue txs that emit accounts' state
        val leafHashes = mutableMapOf<Long, Hash>()
        for (i in 0..15) {
            val l = i.toLong()
            enqueueTx(makeL2StateOp(bcRid, l))
            val state = GtvEncoder.simpleEncodeGtv(GtvFactory.gtv(
                GtvInteger(l),
                GtvByteArray(SECP256K1Keccak.digest(BigInteger.valueOf(l).toByteArray()))))
            leafHashes[l] = SECP256K1Keccak.digest(state)
        }

        // build 1st block and commit
        sealBlock()

        // calculate event root hash
        val l12 = SECP256K1Keccak.treeHasher(leafs[0], leafs[1])
        val l34 = SECP256K1Keccak.treeHasher(leafs[2], leafs[3])
        val eventRootHash = SECP256K1Keccak.treeHasher(l12, l34)

        // calculate state root hash
        val l01 = SECP256K1Keccak.treeHasher(leafHashes[0]!!, leafHashes[1]!!)
        val l23 = SECP256K1Keccak.treeHasher(leafHashes[2]!!, leafHashes[3]!!)
        val hash00 = SECP256K1Keccak.treeHasher(l01, l23)
        val l45 = SECP256K1Keccak.treeHasher(leafHashes[4]!!, leafHashes[5]!!)
        val l67 = SECP256K1Keccak.treeHasher(leafHashes[6]!!, leafHashes[7]!!)
        val hash01 = SECP256K1Keccak.treeHasher(l45, l67)
        val l89 = SECP256K1Keccak.treeHasher(leafHashes[8]!!, leafHashes[9]!!)
        val l1011 = SECP256K1Keccak.treeHasher(leafHashes[10]!!, leafHashes[11]!!)
        val hash10 = SECP256K1Keccak.treeHasher(l89, l1011)
        val l1213 = SECP256K1Keccak.treeHasher(leafHashes[12]!!, leafHashes[13]!!)
        val l1415 = SECP256K1Keccak.treeHasher(leafHashes[14]!!, leafHashes[15]!!)
        val hash11 = SECP256K1Keccak.treeHasher(l1213, l1415)
        val leftHash = SECP256K1Keccak.treeHasher(hash00, hash01)
        val rightHash = SECP256K1Keccak.treeHasher(hash10, hash11)
        val stateRootHash = SECP256K1Keccak.treeHasher(leftHash, rightHash)
        val root = stateRootHash.plus(eventRootHash)

        // query state root from block header's extra data
        val blockHeaderData = getBlockHeaderData(node, currentBlockHeight)
        val extraData = blockHeaderData.gtvExtra
        val l2RootHash = extraData["l2RootHash"]?.asByteArray()

        assertEquals(root.toHex(), l2RootHash!!.toHex())

        val l = 16L
        enqueueTx(makeL2StateOp(bcRid, l))
        val state = GtvEncoder.simpleEncodeGtv(GtvFactory.gtv(
            GtvInteger(l),
            GtvByteArray(SECP256K1Keccak.digest(BigInteger.valueOf(l).toByteArray()))))
        leafHashes[l] = SECP256K1Keccak.digest(state)

        // build 2nd block and commit
        sealBlock()

        // query state root from block header's extra data
        val blockHeaderData2 = getBlockHeaderData(node, currentBlockHeight)
        val extraData2 = blockHeaderData2.gtvExtra
        val l2RootHash2 = extraData2["l2RootHash"]?.asByteArray()
        val l2StateRoot2 = l2RootHash2!!.take(32).toByteArray()

        // calculate state root in new block
        val root2 = SECP256K1Keccak.treeHasher(stateRootHash, leafHashes[l]!!)
        assertEquals(root2.toHex(), l2StateRoot2.toHex())
    }

    private fun getBlockHeaderData(node: PostchainTestNode, height: Long): BlockHeaderData {
        val blockQueries = node.getBlockchainInstance().getEngine().getBlockQueries()
        val blockRid = blockQueries.getBlockRid(height).get()
        val blockHeader = blockQueries.getBlockHeader(blockRid!!).get()
        return BlockHeaderDataFactory.buildFromBinary(blockHeader.rawData)
    }
}
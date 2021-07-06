package net.postchain.el2
//
//import net.postchain.base.SECP256K1CryptoSystem
//import net.postchain.base.getEthereumAddress
//import net.postchain.base.gtv.BlockHeaderData
//import net.postchain.base.gtv.BlockHeaderDataFactory
//import net.postchain.base.snapshot.SimpleDigestSystem
//import net.postchain.common.data.EMPTY_HASH
//import net.postchain.common.data.Hash
//import net.postchain.common.data.KECCAK256
//import net.postchain.common.hexStringToByteArray
//import net.postchain.common.toHex
//import net.postchain.core.BlockchainRid
//import net.postchain.core.Transaction
//import net.postchain.devtools.IntegrationTestSetup
//import net.postchain.devtools.KeyPairHelper
//import net.postchain.devtools.PostchainTestNode
//import net.postchain.gtv.GtvByteArray
//import net.postchain.gtv.GtvEncoder
//import net.postchain.gtv.GtvFactory.gtv
//import net.postchain.gtv.GtvInteger
//import net.postchain.gtv.GtvNull
//import net.postchain.gtx.GTXDataBuilder
//import org.junit.Assert
//import org.junit.Ignore
//import java.math.BigInteger
//import kotlin.test.Test
//import kotlin.test.assertEquals
//import kotlin.test.assertTrue
//
//val myCS = SECP256K1CryptoSystem()
//
//class L2BlockBuilderTest : IntegrationTestSetup() {
//
//    private val ds = SimpleDigestSystem(KECCAK256)
//
//    fun makeL2EventOp(bcRid: BlockchainRid, num: Long): ByteArray {
//        val b = GTXDataBuilder(bcRid, arrayOf(KeyPairHelper.pubKey(0)), myCS)
//        b.addOperation(
//            "el2_event",
//            arrayOf(
//                gtv(num),
//                gtv(ds.digest(BigInteger.valueOf(num).toByteArray()))
//            )
//        )
//        b.finish()
//        b.sign(myCS.buildSigMaker(KeyPairHelper.pubKey(0), KeyPairHelper.privKey(0)))
//        return b.serialize()
//    }
//
//    fun makeL2StateOp(bcRid: BlockchainRid, num: Long): ByteArray {
//        val b = GTXDataBuilder(bcRid, arrayOf(KeyPairHelper.pubKey(0)), myCS)
//        b.addOperation(
//            "el2_state",
//            arrayOf(
//                gtv(num),
//                gtv(num),
//                gtv(ds.digest(BigInteger.valueOf(num).toByteArray()))
//            )
//        )
//        b.finish()
//        b.sign(myCS.buildSigMaker(KeyPairHelper.pubKey(0), KeyPairHelper.privKey(0)))
//        return b.serialize()
//    }
//
//    fun makeNOPGTX(bcRid: BlockchainRid): ByteArray {
//        val b = GTXDataBuilder(bcRid, arrayOf(KeyPairHelper.pubKey(0)), myCS)
//        b.addOperation("nop", arrayOf(gtv(42)))
//        b.finish()
//        b.sign(myCS.buildSigMaker(KeyPairHelper.pubKey(0), KeyPairHelper.privKey(0)))
//        return b.serialize()
//    }
//
//    fun makeTestTx(id: Long, value: String, bcRid: BlockchainRid): ByteArray {
//        val b = GTXDataBuilder(bcRid, arrayOf(KeyPairHelper.pubKey(0)), myCS)
//        b.addOperation("gtx_test", arrayOf(gtv(id), gtv(value)))
//        b.finish()
//        b.sign(myCS.buildSigMaker(KeyPairHelper.pubKey(0), KeyPairHelper.privKey(0)))
//        return b.serialize()
//    }
//
//    fun makeTimeBTx(from: Long, to: Long?, bcRid: BlockchainRid): ByteArray {
//        val b = GTXDataBuilder(bcRid, arrayOf(KeyPairHelper.pubKey(0)), myCS)
//        b.addOperation(
//            "timeb", arrayOf(
//                gtv(from),
//                if (to != null) gtv(to) else GtvNull
//            )
//        )
//        // Need to add a valid dummy operation to make the entire TX valid
//        b.addOperation("gtx_test", arrayOf(gtv(1), gtv("true")))
//        b.finish()
//        b.sign(myCS.buildSigMaker(KeyPairHelper.pubKey(0), KeyPairHelper.privKey(0)))
//        return b.serialize()
//    }
//
//    @Ignore
//    @Test
//    fun testL2BuildBlock() {
//        configOverrides.setProperty("infrastructure", "base/test")
//        val nodes = createNodes(1, "/net/postchain/el2/blockchain_config.xml")
//        val node = nodes[0]
//        val bcRid = systemSetup.blockchainMap[1]!!.rid // Just assume we have chain 1
//
//        fun enqueueTx(data: ByteArray): Transaction? {
//            try {
//                val tx = node.getBlockchainInstance().getEngine().getConfiguration().getTransactionFactory()
//                    .decodeTransaction(data)
//                node.getBlockchainInstance().getEngine().getTransactionQueue().enqueue(tx)
//                return tx
//            } catch (e: Exception) {
//                logger.error(e) { "Can't enqueue tx" }
//            }
//            return null
//        }
//
//        val validTxs = mutableListOf<Transaction>()
//        var currentBlockHeight = -1L
//
//        fun makeSureBlockIsBuiltCorrectly() {
//            currentBlockHeight += 1
//            buildBlockAndCommit(node.getBlockchainInstance().getEngine())
//            Assert.assertEquals(currentBlockHeight, getBestHeight(node))
//            val ridsAtHeight = getTxRidsAtHeight(node, currentBlockHeight)
//            for (vtx in validTxs) {
//                val vtxRID = vtx.getRID()
//                Assert.assertTrue(ridsAtHeight.any { it.contentEquals(vtxRID) })
//            }
//            Assert.assertEquals(validTxs.size, ridsAtHeight.size)
//            validTxs.clear()
//        }
//
//        // Tx1 valid)
//        val validTx1 = enqueueTx(makeTestTx(1, "true", bcRid))!!
//        validTxs.add(validTx1)
//
//        // Tx 2 invalid, b/c bad args
//        enqueueTx(makeTestTx(2, "false", bcRid))!!
//
//        // Tx 3: Nop (invalid, since need more ops)
//        val x = makeNOPGTX(bcRid)
//        enqueueTx(x)
//
////        // Tx: L2 Event Op
////        val validTx2 = enqueueTx(makeL2EventOp(bcRid, 1L))!!
////        validTxs.add(validTx2)
////
////        // Tx: L2 Account State Op
////        val validTx3 = enqueueTx(makeL2StateOp(bcRid, 2L))!!
////        validTxs.add(validTx3)
//
//        // -------------------------
//        // Build it
//        // -------------------------
//        makeSureBlockIsBuiltCorrectly()
//
//        val blockHeaderData = getBlockHeaderData(node, currentBlockHeight)
//        val extraData = blockHeaderData.gtvExtra
//        val l2RootEvent = extraData["l2RootEvent"]?.asByteArray()
//        val l2RootState = extraData["l2RootState"]?.asByteArray()
//        val eventData =
//            "00000000000000000000000000000000000000000000000000000000000000015fe7f977e71dba2ea1a68e21057beebb9be2ac30c6410aa38d4f3fbe41dcffd2".hexStringToByteArray()
//        val eventHash = ds.hash(ds.hash(ds.digest(eventData), EMPTY_HASH), EMPTY_HASH)
//        val stateData =
//            "0000000000000000000000000000000000000000000000000000000000000002f2ee15ea639b73fa3db9b34a245bdfa015c260c598b211bf05a1ecc4b3e3b4f2".hexStringToByteArray()
//        val stateHash = ds.hash(ds.hash(ds.digest(stateData), EMPTY_HASH), EMPTY_HASH)
//        assertEquals(eventHash.toHex(), l2RootEvent!!.toHex())
//        assertEquals(stateHash.toHex(), l2RootState!!.toHex())
//
//        // Tx 4: time, valid, no stop is ok
//        val tx4Time = makeTimeBTx(0, null, bcRid)
//        validTxs.add(enqueueTx(tx4Time)!!)
//
//        // Tx 5: time, valid, from beginning of time to now
//        val tx5Time = makeTimeBTx(0, System.currentTimeMillis(), bcRid)
//        validTxs.add(enqueueTx(tx5Time)!!)
//
//        // TX 6: time, invalid since from bigger than to
//        val tx6Time = makeTimeBTx(100, 0, bcRid)
//        enqueueTx(tx6Time)
//
//        // TX 7: time, invalid since from is in the future
//        val tx7Time = makeTimeBTx(System.currentTimeMillis() + 100, null, bcRid)
//        enqueueTx(tx7Time)
//
//        // -------------------------
//        // Build it
//        // -------------------------
//        makeSureBlockIsBuiltCorrectly()
//
//        val value = node.getBlockchainInstance().getEngine().getBlockQueries().query(
//            """{"type"="gtx_test_get_value", "txRID"="${validTx1.getRID().toHex()}"}"""
//        )
//        Assert.assertEquals("\"true\"", value.get())
//    }
//
//    @Ignore
//    @Test
//    fun testL2UpdateSnapshot() {
//        configOverrides.setProperty("infrastructure", "base/test")
//        val nodes = createNodes(1, "/net/postchain/el2/blockchain_config_1.xml")
//        val node = nodes[0]
//        val bcRid = systemSetup.blockchainMap[1]!!.rid // Just assume we have chain 1
//
//        fun enqueueTx(data: ByteArray): Transaction? {
//            try {
//                val tx = node.getBlockchainInstance().getEngine().getConfiguration().getTransactionFactory()
//                    .decodeTransaction(data)
//                node.getBlockchainInstance().getEngine().getTransactionQueue().enqueue(tx)
//                return tx
//            } catch (e: Exception) {
//                logger.error(e) { "Can't enqueue tx" }
//            }
//            return null
//        }
//
//        var currentBlockHeight = -1L
//
//        fun sealBlock() {
//            currentBlockHeight += 1
//            buildBlockAndCommit(node.getBlockchainInstance().getEngine())
//            Assert.assertEquals(currentBlockHeight, getBestHeight(node))
//        }
//
//        // enqueue txs that emit accounts' state
//        val leafHashes = mutableMapOf<Long, Hash>()
//        for (i in 0..15) {
//            enqueueTx(makeL2StateOp(bcRid, i.toLong()))
//            val l = i.toLong()
//            val state = GtvEncoder.simpleEncodeGtv(
//                gtv(
//                    GtvInteger(l),
//                    GtvByteArray(ds.digest(BigInteger.valueOf(l).toByteArray()))
//                )
//            )
//            leafHashes[l] = ds.digest(state)
//        }
//
//        // build 1st block and commit
//        sealBlock()
//
//        // calculate state root
//        val l01 = ds.hash(leafHashes[0]!!, leafHashes[1]!!)
//        val l23 = ds.hash(leafHashes[2]!!, leafHashes[3]!!)
//        val hash00 = ds.hash(l01, l23)
//        val l45 = ds.hash(leafHashes[4]!!, leafHashes[5]!!)
//        val l67 = ds.hash(leafHashes[6]!!, leafHashes[7]!!)
//        val hash01 = ds.hash(l45, l67)
//        val l89 = ds.hash(leafHashes[8]!!, leafHashes[9]!!)
//        val l1011 = ds.hash(leafHashes[10]!!, leafHashes[11]!!)
//        val hash10 = ds.hash(l89, l1011)
//        val l1213 = ds.hash(leafHashes[12]!!, leafHashes[13]!!)
//        val l1415 = ds.hash(leafHashes[14]!!, leafHashes[15]!!)
//        val hash11 = ds.hash(l1213, l1415)
//        val leftHash = ds.hash(hash00, hash01)
//        val rightHash = ds.hash(hash10, hash11)
//        val root = ds.hash(leftHash, rightHash)
//
//        // query state root from block header's extra data
//        val blockHeaderData = getBlockHeaderData(node, currentBlockHeight)
//        val extraData = blockHeaderData.gtvExtra
//        val l2StateRoot = extraData["l2RootState"]?.asByteArray()
//
//        assertEquals(root.toHex(), l2StateRoot!!.toHex())
//
//        // Verify account state merkle proof
//        for (pos in 0..15) {
//            val args = gtv(
//                "blockHeight" to gtv(currentBlockHeight),
//                "accountNumber" to gtv(pos.toLong())
//            )
//            val gtvProof = node.getBlockchainInstance().getEngine().getBlockQueries().query(
//                "get_account_state_merkle_proof",
//                args
//            ).get().asDict()
//
//            val merkleProofs = gtvProof["merkleProofs"]!!.asArray()
//            val proofs = merkleProofs.map { it.asByteArray() }
//            val stateRoot = getMerkleProof(proofs, pos, leafHashes[pos.toLong()]!!)
//            assertEquals(stateRoot.toHex(), l2StateRoot.toHex())
//
//            val l2RootState = gtvProof["blockHeader"]!!.asByteArray().slice(8*32 until 9*32).toByteArray()
//            assertEquals(stateRoot.toHex(), l2RootState.toHex())
//
//            val accountState = gtvProof["accountState"]!!.asArray()
//            assertEquals(accountState[0].asInteger(), currentBlockHeight)
//            assertEquals(accountState[1].asInteger(), pos.toLong())
//
//            val pubkey = gtvProof["blockWitness"]!!.asArray()[0].asDict()["pubkey"]!!.asByteArray()
//            assertEquals(pubkey.toHex(), getEthereumAddress(node.pubKey.hexStringToByteArray()).toHex())
//        }
//
//        val l = 16L
//        enqueueTx(makeL2StateOp(bcRid, l))
//        val state = GtvEncoder.simpleEncodeGtv(
//            gtv(
//                GtvInteger(l),
//                GtvByteArray(ds.digest(BigInteger.valueOf(l).toByteArray()))
//            )
//        )
//        leafHashes[l] = ds.digest(state)
//
//        // build 2nd block and commit
//        sealBlock()
//
//        // query state root from block header's extra data
//        val blockHeaderData2 = getBlockHeaderData(node, currentBlockHeight)
//        val extraData2 = blockHeaderData2.gtvExtra
//        val l2StateRoot2 = extraData2["l2RootState"]?.asByteArray()
//
//        // calculate the new state root
//        val p5 = ds.hash(ds.hash(leafHashes[l]!!, EMPTY_HASH), EMPTY_HASH)
//        val p7 = ds.hash(ds.hash(p5, EMPTY_HASH), EMPTY_HASH)
//        val root2 = ds.hash(ds.hash(root, p7), EMPTY_HASH)
//        assertEquals(root2.toHex(), l2StateRoot2!!.toHex())
//
//        // Verify account state merkle proof
//        for (pos in 0..16) {
//            val args = gtv(
//                "blockHeight" to gtv(currentBlockHeight),
//                "accountNumber" to gtv(pos.toLong())
//            )
//            val gtvProof = node.getBlockchainInstance().getEngine().getBlockQueries().query(
//                "get_account_state_merkle_proof",
//                args
//            ).get().asDict()
//
//            val merkleProofs = gtvProof["merkleProofs"]!!.asArray()
//            val proofs = merkleProofs.map { it.asByteArray() }
//            val stateRoot = getMerkleProof(proofs, pos, leafHashes[pos.toLong()]!!)
//            assertEquals(stateRoot.toHex(), l2StateRoot2.toHex())
//
//            val l2RootState = gtvProof["blockHeader"]!!.asByteArray().slice(8*32 until 9*32).toByteArray()
//            assertEquals(stateRoot.toHex(), l2RootState.toHex())
//
//            val accountState = gtvProof["accountState"]!!.asArray()
//            assertEquals(accountState[1].asInteger(), pos.toLong())
//
//            val pubkey = gtvProof["blockWitness"]!!.asArray()[0].asDict()["pubkey"]!!.asByteArray()
//            assertEquals(pubkey.toHex(), getEthereumAddress(node.pubKey.hexStringToByteArray()).toHex())
//        }
//    }
//
//    @Ignore
//    @Test
//    fun testL2EventAndUpdateSnapshot() {
//        configOverrides.setProperty("infrastructure", "base/test")
//        val nodes = createNodes(1, "/net/postchain/el2/blockchain_config_1.xml")
//        val node = nodes[0]
//        val bcRid = systemSetup.blockchainMap[1]!!.rid // Just assume we have chain 1
//
//        fun enqueueTx(data: ByteArray): Transaction? {
//            try {
//                val tx = node.getBlockchainInstance().getEngine().getConfiguration().getTransactionFactory()
//                    .decodeTransaction(data)
//                node.getBlockchainInstance().getEngine().getTransactionQueue().enqueue(tx)
//                return tx
//            } catch (e: Exception) {
//                logger.error(e) { "Can't enqueue tx" }
//            }
//            return null
//        }
//
//        var currentBlockHeight = -1L
//
//        fun sealBlock() {
//            currentBlockHeight += 1
//            buildBlockAndCommit(node.getBlockchainInstance().getEngine())
//            Assert.assertEquals(currentBlockHeight, getBestHeight(node))
//        }
//
//        // enqueue txs that emit event
//        val leafs = mutableListOf<Hash>()
//        for (i in 1..4) {
//            val l = i.toLong()
//            enqueueTx(makeL2EventOp(bcRid, l))
//            val event = GtvEncoder.simpleEncodeGtv(
//                gtv(
//                    GtvInteger(l),
//                    GtvByteArray(ds.digest(BigInteger.valueOf(l).toByteArray()))
//                )
//            )
//            leafs.add(ds.digest(event))
//        }
//
//        // enqueue txs that emit accounts' state
//        val leafHashes = mutableMapOf<Long, Hash>()
//        for (i in 0..15) {
//            val l = i.toLong()
//            enqueueTx(makeL2StateOp(bcRid, l))
//            val state = GtvEncoder.simpleEncodeGtv(
//                gtv(
//                    GtvInteger(l),
//                    GtvByteArray(ds.digest(BigInteger.valueOf(l).toByteArray()))
//                )
//            )
//            leafHashes[l] = ds.digest(state)
//        }
//
//        // build 1st block and commit
//        sealBlock()
//
//        // calculate event root hash
//        val l12 = ds.hash(leafs[0], leafs[1])
//        val l34 = ds.hash(leafs[2], leafs[3])
//        val eventRootHash = ds.hash(l12, l34)
//
//        // calculate state root hash
//        val l01 = ds.hash(leafHashes[0]!!, leafHashes[1]!!)
//        val l23 = ds.hash(leafHashes[2]!!, leafHashes[3]!!)
//        val hash00 = ds.hash(l01, l23)
//        val l45 = ds.hash(leafHashes[4]!!, leafHashes[5]!!)
//        val l67 = ds.hash(leafHashes[6]!!, leafHashes[7]!!)
//        val hash01 = ds.hash(l45, l67)
//        val l89 = ds.hash(leafHashes[8]!!, leafHashes[9]!!)
//        val l1011 = ds.hash(leafHashes[10]!!, leafHashes[11]!!)
//        val hash10 = ds.hash(l89, l1011)
//        val l1213 = ds.hash(leafHashes[12]!!, leafHashes[13]!!)
//        val l1415 = ds.hash(leafHashes[14]!!, leafHashes[15]!!)
//        val hash11 = ds.hash(l1213, l1415)
//        val leftHash = ds.hash(hash00, hash01)
//        val rightHash = ds.hash(hash10, hash11)
//        val stateRootHash = ds.hash(leftHash, rightHash)
//
//        // query state root from block header's extra data
//        val blockHeaderData = getBlockHeaderData(node, currentBlockHeight)
//        val extraData = blockHeaderData.gtvExtra
//        val l2RootState = extraData["l2RootState"]?.asByteArray()
//        val l2RootEvent = extraData["l2RootEvent"]?.asByteArray()
//
//        assertEquals(stateRootHash.toHex(), l2RootState!!.toHex())
//        assertEquals(eventRootHash.toHex(), l2RootEvent!!.toHex())
//
//        // Verify event merkle proof
//        for (pos in 0..3) {
//            val args = gtv(
//                "blockHeight" to gtv(currentBlockHeight),
//                "eventHash" to gtv(leafs[pos].toHex())
//            )
//            val gtvProof = node.getBlockchainInstance().getEngine().getBlockQueries().query(
//                "get_event_merkle_proof",
//                args
//            ).get().asDict()
//
//            val merkleProofs = gtvProof["merkleProofs"]!!.asArray()
//            val proofs = merkleProofs.map { it.asByteArray() }
//            val eventRoot = getMerkleProof(proofs, pos, leafs[pos])
//            assertEquals(eventRoot.toHex(), eventRootHash.toHex())
//
//            val l2RootEvent = gtvProof["blockHeader"]!!.asByteArray().slice(7*32 until 8*32).toByteArray()
//            assertEquals(eventRoot.toHex(), l2RootEvent.toHex())
//
//            val eventData = gtvProof["eventData"]!!.asArray()
//            assertEquals(eventData[0].asInteger(), currentBlockHeight)
//            assertEquals(eventData[1].asInteger(), pos.toLong())
//            assertTrue(eventData[2].asByteArray().contentEquals(leafs[pos]))
//
//            val pubkey = gtvProof["blockWitness"]!!.asArray()[0].asDict()["pubkey"]!!.asByteArray()
//            assertEquals(pubkey.toHex(), getEthereumAddress(node.pubKey.hexStringToByteArray()).toHex())
//        }
//
//        val l = 16L
//        enqueueTx(makeL2StateOp(bcRid, l))
//        val state = GtvEncoder.simpleEncodeGtv(
//            gtv(
//                GtvInteger(l),
//                GtvByteArray(ds.digest(BigInteger.valueOf(l).toByteArray()))
//            )
//        )
//        leafHashes[l] = ds.digest(state)
//
//        // build 2nd block and commit
//        sealBlock()
//
//        // query state root from block header's extra data
//        val blockHeaderData2 = getBlockHeaderData(node, currentBlockHeight)
//        val extraData2 = blockHeaderData2.gtvExtra
//        val l2StateRoot2 = extraData2["l2RootState"]?.asByteArray()
//
//        // calculate state root in new block
//        val p5 = ds.hash(ds.hash(leafHashes[l]!!, EMPTY_HASH), EMPTY_HASH)
//        val p7 = ds.hash(ds.hash(p5, EMPTY_HASH), EMPTY_HASH)
//        val root2 = ds.hash(ds.hash(stateRootHash, p7), EMPTY_HASH)
//        assertEquals(root2.toHex(), l2StateRoot2!!.toHex())
//    }
//
//    private fun getBlockHeaderData(node: PostchainTestNode, height: Long): BlockHeaderData {
//        val blockQueries = node.getBlockchainInstance().getEngine().getBlockQueries()
//        val blockRid = blockQueries.getBlockRid(height).get()
//        val blockHeader = blockQueries.getBlockHeader(blockRid!!).get()
//        return BlockHeaderDataFactory.buildFromBinary(blockHeader.rawData)
//    }
//
//    private fun getMerkleProof(proofs: List<Hash>, pos: Int, leaf: Hash): Hash {
//        var r = leaf
//        proofs.forEachIndexed { i, h ->
//            r = if (((pos shr i) and 1) != 0) {
//                ds.hash(h, r)
//            } else {
//                ds.hash(r, h)
//            }
//        }
//        return r
//    }
//}
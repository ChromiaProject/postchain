// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest

import mu.KLogging
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.model.ApiStatus
import net.postchain.api.rest.model.TxRid
import net.postchain.base.BaseBlockWitness
import net.postchain.base.ConfirmationProof
import net.postchain.base.cryptoSystem
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.tx.TransactionStatus
import net.postchain.common.wrap
import net.postchain.core.BlockRid
import net.postchain.core.TransactionInfoExt
import net.postchain.core.TxDetail
import net.postchain.core.block.BlockDetail
import net.postchain.crypto.PubKey
import net.postchain.crypto.Signature
import net.postchain.ebft.rest.contract.StateNodeStatus
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtx.GtxQuery
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RestApiMockForClientManual {
    val listenPort = 49545
    val basePath = "/basepath"
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a1")
    lateinit var restApi: RestApi

    companion object : KLogging()

    @AfterEach
    fun tearDown() {
        restApi.close()
        logger.debug { "Stopped" }
    }

    @Disabled
    @Test
    fun startMockRestApi() {
        val model = MockModel()
        restApi = RestApi(listenPort, basePath, gracefulShutdown = false, clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
        restApi.attachModel(blockchainRID, model)
        logger.info("Ready to serve on port ${restApi.actualPort()}")
        Thread.sleep(600000) // Wait 10 minutes
    }

    class MockModel : Model {
        override val chainIID: Long
            get() = 5L
        override var live = true
        override val blockchainRid: BlockchainRid = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a1")
        override val queryCacheTtlSeconds: Long = 0
        val statusUnknown = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val statusRejected = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val statusConfirmed = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        val statusNotFound = "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
        val statusWaiting = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"

        private val witness = BaseBlockWitness.fromSignatures(arrayOf(
                Signature("0320F0B9E7ECF1A1568C31644B04D37ADC05327F996B9F48220E301DC2FEE6F8FF".hexStringToByteArray(), ByteArray(0)),
                Signature("0307C88BF37C528B14AF95E421749E72F6DA88790BCE74890BDF780D854D063C40".hexStringToByteArray(), ByteArray(0))
        ))
        val blocks = listOf(
                BlockDetail(
                        "blockRid001".toByteArray(),
                        blockchainRid.data, "some header".toByteArray(),
                        0,
                        listOf(),
                        witness.getRawData(),
                        1574849700),
                BlockDetail(
                        "blockRid002".toByteArray(),
                        "blockRid001".toByteArray(),
                        "some other header".toByteArray(),
                        1,
                        listOf(TxDetail("tx1".toByteArray(), "tx1".toByteArray(), "tx1".toByteArray())),
                        witness.getRawData(),
                        1574849760),
                BlockDetail(
                        "blockRid003".toByteArray(),
                        "blockRid002".toByteArray(),
                        "yet another header".toByteArray(),
                        2,
                        listOf(),
                        witness.getRawData(),
                        1574849880),
                BlockDetail(
                        "blockRid004".toByteArray(),
                        "blockRid003".toByteArray(),
                        "guess what? Another header".toByteArray(),
                        3,
                        listOf(
                                TxDetail("tx2".toByteArray(), "tx2".toByteArray(), "tx2".toByteArray()),
                                TxDetail("tx3".toByteArray(), "tx3".toByteArray(), "tx3".toByteArray()),
                                TxDetail("tx4".toByteArray(), "tx4".toByteArray(), "tx4".toByteArray())
                        ),
                        witness.getRawData(),
                        1574849940)
        )

        override fun postTransaction(tx: ByteArray) {
            when (tx.wrap()) {
                "helloOK".toByteArray().wrap() -> return
                "hello400".toByteArray().wrap() -> throw UserMistake("expected error")
                "hello500".toByteArray().wrap() -> throw ProgrammerMistake("expected error")
                else -> throw ProgrammerMistake("unexpected error")
            }
        }

        override fun getTransaction(txRID: TxRid): ByteArray? {
            return when (txRID) {
                TxRid(statusUnknown.hexStringToByteArray()) -> null
                TxRid(statusConfirmed.hexStringToByteArray()) -> "1234".hexStringToByteArray()
                else -> throw ProgrammerMistake("unexpected error")
            }
        }

        override fun getConfirmationProof(txRID: TxRid): ConfirmationProof? {
            return when (txRID) {
                TxRid(statusUnknown.hexStringToByteArray()) -> null
                else -> throw ProgrammerMistake("unexpected error")
            }
        }

        override fun getStatus(txRID: TxRid): ApiStatus {
            return when (txRID) {
                TxRid(statusUnknown.hexStringToByteArray()) -> ApiStatus(TransactionStatus.UNKNOWN)
                TxRid(statusWaiting.hexStringToByteArray()) -> ApiStatus(TransactionStatus.WAITING)
                TxRid(statusConfirmed.hexStringToByteArray()) -> ApiStatus(TransactionStatus.CONFIRMED)
                TxRid(statusRejected.hexStringToByteArray()) -> ApiStatus(TransactionStatus.REJECTED)
                else -> throw ProgrammerMistake("unexpected error")
            }
        }

        override fun query(query: GtxQuery): Gtv {
            return when (query.args) {
                gtv(mapOf("a" to gtv("oknullresponse"), "c" to gtv(3))) -> GtvNull
                gtv(mapOf("a" to gtv("okemptyresponse"), "c" to gtv(3))) -> gtv(mapOf())
                gtv(mapOf("a" to gtv("oksimpleresponse"), "c" to gtv(3))) -> gtv(mapOf("test" to gtv("hi")))
                gtv(mapOf("a" to gtv("usermistake"), "c" to gtv(3))) -> throw UserMistake("expected error")
                gtv(mapOf("a" to gtv("programmermistake"), "c" to gtv(3))) -> throw ProgrammerMistake("expected error")
                else -> throw ProgrammerMistake("unexpected error")
            }
        }

        override fun nodeStatusQuery(): StateNodeStatus = TODO()

        override fun nodePeersStatusQuery(): List<StateNodeStatus> = TODO()

        override fun getBlock(blockRID: BlockRid, txHashesOnly: Boolean): BlockDetail? {
            return (blocks.filter { it.rid.contentEquals(blockRID.data) }).getOrNull(0)
        }

        override fun getBlock(height: Long, txHashesOnly: Boolean): BlockDetail? {
            TODO("Not yet implemented")
        }

        override fun getBlocks(beforeTime: Long, limit: Int, txHashesOnly: Boolean): List<BlockDetail> =
                blocks.filter { it.timestamp < beforeTime }.subList(0, limit)

        override fun getBlocksBeforeHeight(beforeHeight: Long, limit: Int, txHashesOnly: Boolean): List<BlockDetail> =
                blocks.filter { it.height < beforeHeight }.subList(0, limit)

        override fun getCurrentBlockHeight(): BlockHeight {
            TODO("Not yet implemented")
        }

        override fun getBlockchainNodeState(): BlockchainNodeState {
            TODO("Not yet implemented")
        }

        override fun getTransactionInfo(txRID: TxRid): TransactionInfoExt {
            val block = blocks.filter { block -> block.transactions.filter { tx -> cryptoSystem.digest(tx.data!!).contentEquals(txRID.bytes) }.size > 0 }[0]
            val tx = block.transactions.filter { tx -> cryptoSystem.digest(tx.data!!).contentEquals(txRID.bytes) }[0]
            return TransactionInfoExt(block.rid, block.height, block.header, block.witness, block.timestamp, cryptoSystem.digest(tx.data!!), tx.data!!.slice(IntRange(0, 4)).toByteArray(), tx.data!!)
        }

        override fun getTransactionsInfo(beforeTime: Long, limit: Int): List<TransactionInfoExt> {
            var queryBlocks = blocks
            val transactionsInfo: MutableList<TransactionInfoExt> = mutableListOf()
            queryBlocks = queryBlocks.sortedByDescending { blockDetail -> blockDetail.height }
            for (block in queryBlocks) {
                for (tx in block.transactions) {
                    transactionsInfo.add(TransactionInfoExt(block.rid, block.height, block.header, block.witness, block.timestamp, cryptoSystem.digest(tx.data!!), tx.data!!.slice(IntRange(0, 4)).toByteArray(), tx.data!!))
                }
            }
            return transactionsInfo.toList()
        }

        override fun getTransactionsInfoBySigner(beforeTime: Long, limit: Int, signer: PubKey): List<TransactionInfoExt> {
            TODO("Not yet implemented")
        }

        override fun getLastTransactionNumber(): TransactionsCount {
            TODO("Not yet implemented")
        }

        override fun getBlockchainConfiguration(height: Long): ByteArray? {
            TODO("Not yet implemented")
        }

        override fun validateBlockchainConfiguration(configuration: Gtv) {
            TODO("Not yet implemented")
        }
    }
}

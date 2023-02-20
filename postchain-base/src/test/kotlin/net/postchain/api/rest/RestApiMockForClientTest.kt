// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest

import mu.KLogging
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.model.ApiStatus
import net.postchain.api.rest.model.ApiTx
import net.postchain.api.rest.model.TxRID
import net.postchain.base.ConfirmationProof
import net.postchain.base.cryptoSystem
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.tx.TransactionStatus
import net.postchain.core.TransactionInfoExt
import net.postchain.core.TxDetail
import net.postchain.core.block.BlockDetail
import net.postchain.debug.JsonNodeDiagnosticContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtx.GtxQuery
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class RestApiMockForClientManual {
    val listenPort = 49545
    val basePath = "/basepath"
    private val blockchainRID = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a1"
    lateinit var restApi: RestApi

    companion object : KLogging()

    @AfterEach
    fun tearDown() {
        restApi.stop()
        logger.debug { "Stopped" }
    }

    @Disabled
    @Test
    fun startMockRestApi() {
        val model = MockModel()
        restApi = RestApi(listenPort, basePath)
        restApi.attachModel(blockchainRID, model)
        logger.info("Ready to serve on port ${restApi.actualPort()}")
        Thread.sleep(600000) // Wait 10 minutes
    }

    class MockModel : Model {
        override val chainIID: Long
            get() = 5L
        override var live = true
        private val blockchainRID = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a1"
        val statusUnknown = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val statusRejected = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val statusConfirmed = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        val statusNotFound = "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
        val statusWaiting = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"

        val blocks = listOf<BlockDetail>(
                BlockDetail(
                        "blockRid001".toByteArray(),
                        blockchainRID.toByteArray(), "some header".toByteArray(),
                        0,
                        listOf<TxDetail>(),
                        "signatures".toByteArray(),
                        1574849700),
                BlockDetail(
                        "blockRid002".toByteArray(),
                        "blockRid001".toByteArray(),
                        "some other header".toByteArray(),
                        1,
                        listOf<TxDetail>(TxDetail("tx1".toByteArray(), "tx1".toByteArray(), "tx1".toByteArray())),
                        "signatures".toByteArray(),
                        1574849760),
                BlockDetail(
                        "blockRid003".toByteArray(),
                        "blockRid002".toByteArray(),
                        "yet another header".toByteArray(),
                        2,
                        listOf<TxDetail>(),
                        "signatures".toByteArray(),
                        1574849880),
                BlockDetail(
                        "blockRid004".toByteArray(),
                        "blockRid003".toByteArray(),
                        "guess what? Another header".toByteArray(),
                        3,
                        listOf<TxDetail>(
                                TxDetail("tx2".toByteArray(), "tx2".toByteArray(), "tx2".toByteArray()),
                                TxDetail("tx3".toByteArray(), "tx3".toByteArray(), "tx3".toByteArray()),
                                TxDetail("tx4".toByteArray(), "tx4".toByteArray(), "tx4".toByteArray())
                        ),
                        "signatures".toByteArray(),
                        1574849940)
        )

        override fun postTransaction(tx: ApiTx) {
            when (tx.tx) {
                "helloOK".toByteArray().toHex() -> return
                "hello400".toByteArray().toHex() -> throw UserMistake("expected error")
                "hello500".toByteArray().toHex() -> throw ProgrammerMistake("expected error")
                else -> throw ProgrammerMistake("unexpected error")
            }
        }

        override fun getTransaction(txRID: TxRID): ApiTx? {
            return when (txRID) {
                TxRID(statusUnknown.hexStringToByteArray()) -> null
                TxRID(statusConfirmed.hexStringToByteArray()) -> ApiTx("1234")
                else -> throw ProgrammerMistake("unexpected error")
            }
        }

        override fun getConfirmationProof(txRID: TxRID): ConfirmationProof? {
            return when (txRID) {
                TxRID(statusUnknown.hexStringToByteArray()) -> null
                else -> throw ProgrammerMistake("unexpected error")
            }
        }

        override fun getStatus(txRID: TxRID): ApiStatus {
            return when (txRID) {
                TxRID(statusUnknown.hexStringToByteArray()) -> ApiStatus(TransactionStatus.UNKNOWN)
                TxRID(statusWaiting.hexStringToByteArray()) -> ApiStatus(TransactionStatus.WAITING)
                TxRID(statusConfirmed.hexStringToByteArray()) -> ApiStatus(TransactionStatus.CONFIRMED)
                TxRID(statusRejected.hexStringToByteArray()) -> ApiStatus(TransactionStatus.REJECTED)
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

        override fun nodeQuery(subQuery: String): String = TODO()

        override fun getBlock(blockRID: ByteArray, txHashesOnly: Boolean): BlockDetail? {
            return (blocks.filter { it.rid.contentEquals(blockRID) }).getOrNull(0)
        }

        override fun getBlock(height: Long, txHashesOnly: Boolean): BlockDetail? {
            TODO("Not yet implemented")
        }

        override fun getBlocks(beforeTime: Long, limit: Int, txHashesOnly: Boolean): List<BlockDetail> =
                blocks.filter { it.timestamp < beforeTime }.subList(0, limit)

        override fun getBlocksBeforeHeight(beforeHeight: Long, limit: Int, txHashesOnly: Boolean): List<BlockDetail> =
                blocks.filter { it.height < beforeHeight }.subList(0, limit)

        override fun getTransactionInfo(txRID: TxRID): TransactionInfoExt {
            val block = blocks.filter { block -> block.transactions.filter { tx -> cryptoSystem.digest(tx.data!!).contentEquals(txRID.bytes) }.size > 0 }[0]
            val tx = block.transactions.filter { tx -> cryptoSystem.digest(tx.data!!).contentEquals(txRID.bytes) }[0]
            return TransactionInfoExt(block.rid, block.height, block.header, block.witness, block.timestamp, cryptoSystem.digest(tx.data!!), tx.data!!.slice(IntRange(0, 4)).toByteArray(), tx.data!!)
        }

        override fun getTransactionsInfo(beforeTime: Long, limit: Int): List<TransactionInfoExt> {
            var queryBlocks = blocks
            var transactionsInfo: MutableList<TransactionInfoExt> = mutableListOf()
            queryBlocks = queryBlocks.sortedByDescending { blockDetail -> blockDetail.height }
            for (block in queryBlocks) {
                for (tx in block.transactions) {
                    transactionsInfo.add(TransactionInfoExt(block.rid, block.height, block.header, block.witness, block.timestamp, cryptoSystem.digest(tx.data!!), tx.data!!.slice(IntRange(0, 4)).toByteArray(), tx.data!!))
                }
            }
            return transactionsInfo.toList()
        }

        override fun debugQuery(subQuery: String?): String {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getBlockchainConfiguration(height: Long): ByteArray? {
            TODO("Not yet implemented")
        }

    }
}

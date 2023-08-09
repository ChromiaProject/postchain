// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest

import io.restassured.RestAssured.given
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.configurations.GTXTestModule
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.devtools.KeyPairHelper.privKey
import net.postchain.crypto.devtools.KeyPairHelper.pubKey
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.GTXTransactionFactory
import net.postchain.gtx.GtxBuilder
import org.hamcrest.core.IsEqual.equalTo
import java.util.Random

class RestApiTestManual {
    private val port = 58373
    private val cryptoSystem = Secp256K1CryptoSystem()
    // TODO Olle POS-93 where do we get it?
    private var blockchainRID: BlockchainRid? = null //"78967BAA4768CBCEF11C508326FFB13A956689FCB6DC3BA17F4B895CBB1577A3"

    //    @Test
    fun testGtxTestModuleBackend() {
        org.awaitility.Awaitility.await()

        val query = """{"type"="gtx_test_get_value", "txRID"="abcd"}"""
        given().port(port)
                .body(query)
                .post("/query")
                .then()
                .statusCode(200)
                .body(equalTo("null"))

        val txBytes = buildTestTx(1L, "hello${Random().nextLong()}")
        given().port(port)
                .body("""{"tx"="${txBytes.toHex()}"}""")
                .post("/tx")
                .then()
                .statusCode(200)

        GTXTransactionFactory(BlockchainRid.ZERO_RID, GTXTestModule(), cryptoSystem)
                .decodeTransaction(txBytes)
        //RestTools.awaitConfirmed(port, blockchainRID!!.toHex(), transaction.getRID().toHex())
    }

    private fun buildTestTx(id: Long, value: String): ByteArray {
        val b = GtxBuilder(BlockchainRid.ZERO_RID, listOf(pubKey(0)), cryptoSystem)
            .addOperation("gtx_test", gtv(id), gtv(value))
            .finish()
            .sign(cryptoSystem.buildSigMaker(KeyPair(pubKey(0), privKey(0))))
        return b.buildGtx().encode()
    }
}
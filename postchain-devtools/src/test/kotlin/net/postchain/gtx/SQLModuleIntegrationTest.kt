// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx

import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.concurrent.util.get
import net.postchain.crypto.KeyPair
import net.postchain.crypto.devtools.KeyPairHelper.privKey
import net.postchain.crypto.devtools.KeyPairHelper.pubKey
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SQLModuleIntegrationTest : IntegrationTestSetup() {

    private fun makeTx(ownerIdx: Int, key: String, value: String, bcRid: BlockchainRid): ByteArray {
        val owner = pubKey(ownerIdx)
        return GtxBuilder(bcRid, listOf(owner), net.postchain.devtools.gtx.myCS)
                .addOperation("test_set_value", gtv(key), gtv(value), gtv(owner))
                .finish()
                .sign(net.postchain.devtools.gtx.myCS.buildSigMaker(KeyPair(owner, privKey(ownerIdx))))
                .buildGtx()
                .encode()
    }


    @Test
    fun testBuildBlock() {
        configOverrides.setProperty("infrastructure", "base/test")
        val nodes = createNodes(1, "/net/postchain/devtools/gtx/blockchain_config.xml")
        val node = nodes[0]
        val bcRid = node.getBlockchainRid(1L)!!

        enqueueTx(node, makeTx(0, "k", "v", bcRid), 0)
        buildBlockAndCommit(node)
        enqueueTx(node, makeTx(0, "k", "v2", bcRid), 1)
        enqueueTx(node, makeTx(0, "k2", "v2", bcRid), 1)
        enqueueTx(node, makeTx(1, "k", "v", bcRid), -1)
        buildBlockAndCommit(node)

        verifyBlockchainTransactions(node)

        val blockQueries = node.getBlockchainInstance().blockchainEngine.getBlockQueries()
        assertFailsWith<UserMistake> {
            blockQueries.query("non-existing", gtv(mapOf())).get()
        }

        // ------------------------------------------
        // Shouldn't find key "hello" in type "test_get_value"
        // ------------------------------------------
        val result = blockQueries.query("test_get_value", gtv(mapOf("q_key" to gtv("hello")))).get()
        assertEquals(0, result.asArray().size)

        // ------------------------------------------
        // Should find 1 hit for key "k" in type "test_get_value"
        // ------------------------------------------
        val result1 = blockQueries.query("test_get_value", gtv(mapOf("q_key" to gtv("k")))).get()
        assertEquals(1, result1.asArray().size)

        val hit0 = result1[0].asDict()
        assertNotNull(hit0["val"])
        assertEquals("v2", hit0["val"]!!.asString())
        assertNotNull(hit0["owner"])
        assertTrue(pubKey(0).contentEquals(hit0["owner"]!!.asByteArray(true)))

        // ------------------------------------------
        // Look for type "test_get_count"
        // ------------------------------------------
        val result2 = blockQueries.query("test_get_count", gtv(mapOf())).get()
        val result2Arr = result2.asArray()
        assertEquals(1, result2Arr.size)
        assertEquals(1, result2Arr[0]["nbigint"]!!.asInteger())
        assertEquals(2, result2Arr[0]["ncount"]!!.asInteger())

        println(result2)
    }

    @Test
    fun testQueryWithMultipleParams() {
        configOverrides.setProperty("infrastructure", "base/test")
        val nodes = createNodes(1, "/net/postchain/devtools/gtx/blockchain_config.xml")
        val node = nodes[0]
        val bcRid = node.getBlockchainRid(1L)!!

        enqueueTx(node, makeTx(0, "k", "v", bcRid), 0)
        buildBlockAndCommit(node)
        verifyBlockchainTransactions(node)
        val blockQueries = node.getBlockchainInstance().blockchainEngine.getBlockQueries()
        val result = blockQueries.query("test_get_value", gtv(mapOf("q_key" to gtv("k"), "q_value" to gtv("v")))).get()
        assertEquals(1, result.asArray().size)
    }

    @Test
    fun testQuerySupportNullableValue() {
        configOverrides.setProperty("infrastructure", "base/test")
        val nodes = createNodes(1, "/net/postchain/devtools/gtx/blockchain_config.xml")
        val node = nodes[0]
        val bcRid = node.getBlockchainRid(1L)!!

        enqueueTx(node, makeTx(0, "k", "v", bcRid), 0)
        buildBlockAndCommit(node)
        verifyBlockchainTransactions(node)

        val blockQueries = node.getBlockchainInstance().blockchainEngine.getBlockQueries()
        val result = blockQueries.query("test_null_value", gtv(mapOf())).get()

        val hit0 = result[0].asDict()
        assertNotNull(hit0["val"])
        assertEquals(GtvNull, hit0["val"])
    }
}
// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx

import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.crypto.devtools.KeyPairHelper.privKey
import net.postchain.crypto.devtools.KeyPairHelper.pubKey
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.gtv.*
import net.postchain.gtv.GtvFactory.gtv
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
            .sign(net.postchain.devtools.gtx.myCS.buildSigMaker(owner, privKey(ownerIdx)))
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
            blockQueries.query("""{tdype: 'test_get_value'}""")
        }

        assertFailsWith<UserMistake> {
            blockQueries.query("""{type: 'non-existing'}""")
        }

        val gson = make_gtv_gson()

        // ------------------------------------------
        // Shouldn't find key "hello" in type "test_get_value"
        // ------------------------------------------
        val result = blockQueries.query("""{type: 'test_get_value', q_key: 'hello'}""")
        val gtvResult = gson.fromJson<Gtv>(result, Gtv::class.java) as GtvCollection
        assertEquals(0, gtvResult.getSize())

        // ------------------------------------------
        // Should find 1 hit for key "k" in type "test_get_value"
        // ------------------------------------------
        val result1 = blockQueries.query("""{type: 'test_get_value', q_key: 'k'}""")
        val gtvArrRes1 = gson.fromJson<Gtv>(result1, Gtv::class.java) as GtvArray
        assertEquals(1, gtvArrRes1.getSize())

        val hit0 = gtvArrRes1[0].asDict()
        assertNotNull(hit0["val"])
        assertEquals("v2", hit0["val"]!!.asString())
        assertNotNull(hit0["owner"])
        assertTrue(pubKey(0).contentEquals(hit0["owner"]!!.asByteArray(true)))

        // ------------------------------------------
        // Look for type "test_get_count"
        // ------------------------------------------
        val result2 = blockQueries.query("""{type: 'test_get_count'}""")
        val gtvArrRes2 = gson.fromJson<Gtv>(result2, Gtv::class.java) as GtvArray
        assertEquals(1, gtvArrRes2.getSize())
        assertEquals(1, gtvArrRes2[0]["nbigint"]!!.asInteger())
        assertEquals(2, gtvArrRes2[0]["ncount"]!!.asInteger())

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
        val gson = make_gtv_gson()
        var result = blockQueries.query("""{type: 'test_get_value', q_key: 'k', q_value : 'v'}""")
        var gtxResult = gson.fromJson<Gtv>(result, Gtv::class.java) as GtvArray
        assertEquals(1, gtxResult.getSize())
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
        var result = blockQueries.query("""{type: 'test_null_value'}""")
        val gson = make_gtv_gson()
        var gtxResult = gson.fromJson<Gtv>(result, Gtv::class.java)

        val hit0 = gtxResult.get(0).asDict()
        assertNotNull(hit0.get("val"))
        assertEquals(GtvNull, hit0.get("val"))
    }
}
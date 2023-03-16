package net.postchain.integrationtest

import assertk.assert
import assertk.assertions.isEqualTo
import net.postchain.PostchainContext
import net.postchain.common.BlockchainRid
import net.postchain.concurrent.util.get
import net.postchain.core.BlockchainProcess
import net.postchain.core.EContext
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.addBlockchainAndStart
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.SimpleGTXModule
import org.junit.jupiter.api.Test

class CrossBlockchainQueryTest : IntegrationTestSetup() {

    @Test
    fun queryAnotherBlockchain() {
        val nodes = createNodes(1, "/net/postchain/devtools/cross_bc_query/blockchain_config_1.xml")

        val blockchainRid1 = nodes[0].getBlockchainRid(1L)

        CrossBlockchainTestSynchronizationInfrastructureExtension.chainOneRid = blockchainRid1

        val blockchainConfig2 = readBlockchainConfig("/net/postchain/devtools/cross_bc_query/blockchain_config_2.xml")

        nodes[0].addBlockchainAndStart(2L, blockchainConfig2)

        assert(CrossBlockchainTestSynchronizationInfrastructureExtension.queryResult).isEqualTo(gtv(1L))
    }
}

class CrossBlockchainTestGTXModule : SimpleGTXModule<Unit>(Unit,
        mapOf(),
        mapOf("test_query" to { _, _, _ ->
            gtv(1L)
        })
) {
    override fun initializeDB(ctx: EContext) {}
}

class CrossBlockchainTestSynchronizationInfrastructureExtension(private val postchainContext: PostchainContext) : SynchronizationInfrastructureExtension {
    companion object {
        var queryResult: Gtv? = null
        var chainOneRid: BlockchainRid? = null
    }

    override fun shutdown() {}

    override fun connectProcess(process: BlockchainProcess) {
        val chainOneBlockQueries = postchainContext.blockQueriesProvider.getBlockQueries(chainOneRid!!)
        queryResult = chainOneBlockQueries!!.query("test_query", gtv(mapOf())).get()
    }

    override fun disconnectProcess(process: BlockchainProcess) {}
}

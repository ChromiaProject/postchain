package net.postchain.base.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.StorageBuilder
import net.postchain.base.BaseBlockQueries
import net.postchain.base.TestBlockChainBuilder
import net.postchain.concurrent.util.get
import net.postchain.config.app.AppConfig
import net.postchain.gtv.gtvml.GtvMLParser
import org.junit.jupiter.api.Test

class BaseBlockQueriesTest {

    private val appConfig: AppConfig = testDbConfig("base_block_queries")
    private val configData0 = GtvMLParser.parseGtvML(javaClass.getResource("../importexport/blockchain_configuration_0.xml")!!.readText())
    private val configData2 = GtvMLParser.parseGtvML(javaClass.getResource("../importexport/blockchain_configuration_2.xml")!!.readText())

    @Test
    fun `test basic usage of getBlocksFromHeight`() {

        StorageBuilder.buildStorage(appConfig, wipeDatabase = true)
                .use { storage ->

                    val blockChainBuilder = TestBlockChainBuilder(storage, configData0)

                    blockChainBuilder.buildBlockchainWithEmptyBlocks(listOf(0L to configData0, 2L to configData2), 4)

                    val baseBlockQueries = BaseBlockQueries(
                            blockChainBuilder.cryptoSystem,
                            storage,
                            BaseBlockStore(),
                            blockChainBuilder.chainId,
                            "".toByteArray()
                    )

                    // Get 2 blocks, 0-1 - limit is hit
                    var blockDetails = baseBlockQueries.getBlocksFromHeight(0, 2, true).get()
                    assertThat(blockDetails.size).isEqualTo(2)

                    // Get 2 blocks, 0-4 (the end) - limit is not hit
                    blockDetails = baseBlockQueries.getBlocksFromHeight(2, 10000, true).get()
                    assertThat(blockDetails.size).isEqualTo(2)
                }
    }
}

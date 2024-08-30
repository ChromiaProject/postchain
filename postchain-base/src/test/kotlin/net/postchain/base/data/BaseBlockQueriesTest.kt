package net.postchain.base.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.StorageBuilder
import net.postchain.base.BaseBlockQueries
import net.postchain.base.TestBlockchainBuilder
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

                    val blockChainBuilder = TestBlockchainBuilder(storage, configData0)

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

    @Test
    fun `get blocks with max data size`() {
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true)
                .use { storage ->

                    val blockChainBuilder = TestBlockchainBuilder(storage, configData0)

                    blockChainBuilder.buildBlockchainWithTestTransactions(listOf(0L to configData0), listOf(
                            listOf("first"),
                            listOf("second"),
                            listOf("third")
                    ))

                    val baseBlockQueries = BaseBlockQueries(
                            blockChainBuilder.cryptoSystem,
                            storage,
                            BaseBlockStore(),
                            blockChainBuilder.chainId,
                            "".toByteArray()
                    )

                    val blockDetailsTruncated = baseBlockQueries.getBlocks(Long.MAX_VALUE, 10000, false,1658).get()
                    assertThat(blockDetailsTruncated.blockDetails.size).isEqualTo(1)
                    assertThat(blockDetailsTruncated.remainingTruncatedCount).isEqualTo(2)

                    val allBlockDetails = baseBlockQueries.getBlocks(Long.MAX_VALUE, 10000, false,5000).get()
                    assertThat(allBlockDetails.blockDetails.size).isEqualTo(3)
                    assertThat(allBlockDetails.remainingTruncatedCount).isEqualTo(0)
                }
    }

    @Test
    fun `getBlocksBeforeHeight with max data size`() {

        StorageBuilder.buildStorage(appConfig, wipeDatabase = true)
                .use { storage ->

                    val blockChainBuilder = TestBlockchainBuilder(storage, configData0)

                    blockChainBuilder.buildBlockchainWithTestTransactions(listOf(0L to configData0), listOf(
                            listOf("first"),
                            listOf("second"),
                            listOf("third")
                    ))

                    val baseBlockQueries = BaseBlockQueries(
                            blockChainBuilder.cryptoSystem,
                            storage,
                            BaseBlockStore(),
                            blockChainBuilder.chainId,
                            "".toByteArray()
                    )

                    val blockDetailsTruncated = baseBlockQueries.getBlocksBeforeHeight(Long.MAX_VALUE, 10000, false,1658).get()
                    assertThat(blockDetailsTruncated.blockDetails.size).isEqualTo(1)
                    assertThat(blockDetailsTruncated.remainingTruncatedCount).isEqualTo(2)

                    val allBlockDetails = baseBlockQueries.getBlocksBeforeHeight(Long.MAX_VALUE, 10000, false,5000).get()
                    assertThat(allBlockDetails.blockDetails.size).isEqualTo(3)
                    assertThat(allBlockDetails.remainingTruncatedCount).isEqualTo(0)
                }
    }
}

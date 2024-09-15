package net.postchain.base.data

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.StorageBuilder
import net.postchain.base.BaseBlockQueries
import net.postchain.base.TestBlockchainBuilder
import net.postchain.common.hexStringToByteArray
import net.postchain.concurrent.util.get
import net.postchain.config.app.AppConfig
import net.postchain.core.TxDetail
import net.postchain.core.block.BlockQueryHeightFilter
import net.postchain.core.block.BlockQueryTimeFilter
import net.postchain.gtv.gtvml.GtvMLParser
import org.junit.jupiter.api.Test

class BaseBlockQueriesTest {

    private val appConfig: AppConfig = testDbConfig("base_block_queries")
    private val configData0 = GtvMLParser.parseGtvML(javaClass.getResource("../importexport/blockchain_configuration_0.xml")!!.readText())
    private val configData2 = GtvMLParser.parseGtvML(javaClass.getResource("../importexport/blockchain_configuration_2.xml")!!.readText())

    @Test
    fun `basic usage of getBlocksFromHeight`() {
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true)
                .use { storage ->

                    val blockChainBuilder = TestBlockchainBuilder(storage, configData0)

                    blockChainBuilder.buildBlockchain(listOf(0L to configData0, 2L to configData2), 4)

                    val baseBlockQueries = BaseBlockQueries(
                            blockChainBuilder.cryptoSystem,
                            storage,
                            BaseBlockStore(),
                            blockChainBuilder.chainId,
                            "".toByteArray()
                    )

                    // Get 2 blocks, 0-1 - limit is hit
                    var blockDetails = baseBlockQueries.getBlocksFromHeight(0, 2).get()
                    assertThat(blockDetails.size).isEqualTo(2)

                    // Get 2 blocks, 0-4 (the end) - limit is not hit
                    blockDetails = baseBlockQueries.getBlocksFromHeight(2, 10000).get()
                    assertThat(blockDetails.size).isEqualTo(2)
                }
    }

    @Test
    fun `getBlocksBetweenTimes with max data size`() {
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

                    val blockDetailsTruncated = baseBlockQueries.getBlocksBetweenTimes(BlockQueryTimeFilter(), 10000, false, 1658, false).get()
                    assertThat(blockDetailsTruncated.blockDetails.size).isEqualTo(1)
                    assertThat(blockDetailsTruncated.truncated).isTrue()

                    val allBlockDetails = baseBlockQueries.getBlocksBetweenTimes(BlockQueryTimeFilter(), 10000, false, 5000, false).get()
                    assertThat(allBlockDetails.blockDetails.size).isEqualTo(3)
                    assertThat(allBlockDetails.truncated).isFalse()
                }
    }

    @Test
    fun `getBlocksBetweenHeights with max data size`() {
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

                    val blockDetailsTruncated = baseBlockQueries.getBlocksBetweenHeights(BlockQueryHeightFilter(), 10000, false, 1658, false).get()
                    assertThat(blockDetailsTruncated.blockDetails.size).isEqualTo(1)
                    assertThat(blockDetailsTruncated.truncated).isTrue()

                    val allBlockDetails = baseBlockQueries.getBlocksBetweenHeights(BlockQueryHeightFilter(), 10000, false, 5000, false).get()
                    assertThat(allBlockDetails.blockDetails.size).isEqualTo(3)
                    assertThat(allBlockDetails.truncated).isFalse()
                }
    }

    @Test
    fun `getBlocksBetweenTimes with empty blocks`() {
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true)
                .use { storage ->

                    val blockChainBuilder = TestBlockchainBuilder(storage, configData0)

                    blockChainBuilder.buildBlockchainWithTestTransactions(listOf(0L to configData0), listOf(
                            listOf("first", "second"),
                            listOf(),
                            listOf("third")
                    ))

                    val baseBlockQueries = BaseBlockQueries(
                            blockChainBuilder.cryptoSystem,
                            storage,
                            BaseBlockStore(),
                            blockChainBuilder.chainId,
                            "".toByteArray()
                    )

                    val allBlockDetails = baseBlockQueries.getBlocksBetweenTimes(BlockQueryTimeFilter(), 10000, true, 10000, false).get()
                    assertThat(allBlockDetails.blockDetails.size).isEqualTo(3)
                    assertThat(allBlockDetails.truncated).isFalse()
                    assertThat(allBlockDetails.blockDetails[0].transactions).containsExactly(
                            TxDetail("7B60FCD5BEC6DDFB327BFB9BEB9E779B105042FB9F921D56D94B282E250ACBBB".hexStringToByteArray(),
                                    "153323D1521D961D92CED38F5AA4932F4ACA00660165C6CD1088577531713D7F".hexStringToByteArray(),
                                    null)
                    )
                    assertThat(allBlockDetails.blockDetails[1].transactions).isEmpty()
                    assertThat(allBlockDetails.blockDetails[2].transactions).containsExactly(
                            TxDetail("923D459667CD90107C858DF657B58C06F5CB8D13B66BB299B9B9981C66B67C5F".hexStringToByteArray(),
                                    "71456E9978317DD4FFE5053E9A3AEDDAE147302E126F197BD71960D1D10E2C72".hexStringToByteArray(),
                                    null),
                            TxDetail("3D662AFC4430E74193304B9450005C3D395007C098F3E4A3C3C20DF29FFBD67D".hexStringToByteArray(),
                                    "DDAC4657A502E00E1693A9E9ECB4F19FB9C2B39109734F4E7DEC2279A1E7C064".hexStringToByteArray(),
                                    null)
                    )

                    val allBlockDetails2 = baseBlockQueries.getBlocksBetweenTimes(BlockQueryTimeFilter(), 10000, true, 10000, true).get()
                    assertThat(allBlockDetails2.blockDetails.size).isEqualTo(2)
                    assertThat(allBlockDetails2.truncated).isFalse()
                    assertThat(allBlockDetails2.blockDetails[0].transactions).containsExactly(
                            TxDetail("7B60FCD5BEC6DDFB327BFB9BEB9E779B105042FB9F921D56D94B282E250ACBBB".hexStringToByteArray(),
                                    "153323D1521D961D92CED38F5AA4932F4ACA00660165C6CD1088577531713D7F".hexStringToByteArray(),
                                    null)
                    )
                    assertThat(allBlockDetails2.blockDetails[1].transactions).containsExactly(
                            TxDetail("923D459667CD90107C858DF657B58C06F5CB8D13B66BB299B9B9981C66B67C5F".hexStringToByteArray(),
                                    "71456E9978317DD4FFE5053E9A3AEDDAE147302E126F197BD71960D1D10E2C72".hexStringToByteArray(),
                                    null),
                            TxDetail("3D662AFC4430E74193304B9450005C3D395007C098F3E4A3C3C20DF29FFBD67D".hexStringToByteArray(),
                                    "DDAC4657A502E00E1693A9E9ECB4F19FB9C2B39109734F4E7DEC2279A1E7C064".hexStringToByteArray(),
                                    null)
                    )
                }
    }
}

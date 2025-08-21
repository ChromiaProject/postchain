package net.postchain.base.data

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isBetween
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.StorageBuilder
import net.postchain.base.TestBlockQueries
import net.postchain.base.TestBlockchainBuilder
import net.postchain.common.hexStringToByteArray
import net.postchain.concurrent.util.get
import net.postchain.config.app.AppConfig
import net.postchain.core.Storage
import net.postchain.core.TxDetail
import net.postchain.core.block.BlockQueryHeightFilter
import net.postchain.core.block.BlockQueryTimeFilter
import net.postchain.gtv.GtvNull
import net.postchain.gtv.GtvString
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtx.GTXBlockQueries
import net.postchain.gtx.GTXModule
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis

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

                    val baseBlockQueries = TestBlockQueries(
                            storage,
                            BaseBlockStore(),
                            blockChainBuilder.chainId,
                            "".toByteArray(),
                            blockChainBuilder.hashCalculator,
                            mock()
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

                    val baseBlockQueries = TestBlockQueries(
                            storage,
                            BaseBlockStore(),
                            blockChainBuilder.chainId,
                            "".toByteArray(),
                            blockChainBuilder.hashCalculator,
                            mock()
                    )

                    val blockDetailsTruncated = baseBlockQueries.getBlocksBetweenTimes(BlockQueryTimeFilter(), 10000, false, 2000, false).get()
                    assertThat(blockDetailsTruncated.blockDetails.size).isEqualTo(1)
                    assertThat(blockDetailsTruncated.truncated).isTrue()

                    val allBlockDetails = baseBlockQueries.getBlocksBetweenTimes(BlockQueryTimeFilter(), 10000, false, 6000, false).get()
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

                    val baseBlockQueries = TestBlockQueries(
                            storage,
                            BaseBlockStore(),
                            blockChainBuilder.chainId,
                            "".toByteArray(),
                            blockChainBuilder.hashCalculator,
                            mock()
                    )

                    val blockDetailsTruncated = baseBlockQueries.getBlocksBetweenHeights(BlockQueryHeightFilter(), 10000, false, 2000, false).get()
                    assertThat(blockDetailsTruncated.blockDetails.size).isEqualTo(1)
                    assertThat(blockDetailsTruncated.truncated).isTrue()

                    val allBlockDetails = baseBlockQueries.getBlocksBetweenHeights(BlockQueryHeightFilter(), 10000, false, 6000, false).get()
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

                    val baseBlockQueries = TestBlockQueries(
                            storage,
                            BaseBlockStore(),
                            blockChainBuilder.chainId,
                            "".toByteArray(),
                            blockChainBuilder.hashCalculator,
                            mock()
                    )

                    val allBlockDetails = baseBlockQueries.getBlocksBetweenTimes(BlockQueryTimeFilter(), 10000, true, 10000, false).get()
                    assertThat(allBlockDetails.blockDetails.size).isEqualTo(3)
                    assertThat(allBlockDetails.truncated).isFalse()
                    assertThat(allBlockDetails.blockDetails[0].transactions).containsExactly(
                            TxDetail(rid="3AD6589D83787DA4A8E9973F768CA65E8BA2D866020A85F207C21AC5800009C7".hexStringToByteArray(),
                                     hash="086D36E5AFDFF627F803BEDCE51790DED8E69F9CCA826A6F1E126B4ED6F44E7A".hexStringToByteArray(),
                                     data=null)
                    )
                    assertThat(allBlockDetails.blockDetails[1].transactions).isEmpty()
                    assertThat(allBlockDetails.blockDetails[2].transactions).containsExactly(
                            TxDetail(rid="AA3DE19612E0E211CC5B87CF2C9A4D53646AB5C1A1E74FD9E15F2454914BB11F".hexStringToByteArray(),
                                     hash="2C42B82C0C0DA51A46B9634ECC82215F153DE8A26B0C5F30FCA0CAD2F569FFDE".hexStringToByteArray(),
                                     data=null),
                            TxDetail(rid="C7843B794006D94305DEABBB5B211B1A3DF76A953F5535CD84750266DDDF54DA".hexStringToByteArray(),
                                     hash="A2A5A9D10DB3EBE135E8DB3335B52F02839050ED0A485D2BA612B9827510EB29".hexStringToByteArray(),
                                     data=null)
                    )

                    val allBlockDetails2 = baseBlockQueries.getBlocksBetweenTimes(BlockQueryTimeFilter(), 10000, true, 10000, true).get()
                    assertThat(allBlockDetails2.blockDetails.size).isEqualTo(2)
                    assertThat(allBlockDetails2.truncated).isFalse()
                    assertThat(allBlockDetails2.blockDetails[0].transactions).containsExactly(
                            TxDetail(rid="3AD6589D83787DA4A8E9973F768CA65E8BA2D866020A85F207C21AC5800009C7".hexStringToByteArray(),
                                     hash="086D36E5AFDFF627F803BEDCE51790DED8E69F9CCA826A6F1E126B4ED6F44E7A".hexStringToByteArray(),
                                     data=null)
                    )
                    assertThat(allBlockDetails2.blockDetails[1].transactions).containsExactly(
                            TxDetail(rid="AA3DE19612E0E211CC5B87CF2C9A4D53646AB5C1A1E74FD9E15F2454914BB11F".hexStringToByteArray(),
                                     hash="2C42B82C0C0DA51A46B9634ECC82215F153DE8A26B0C5F30FCA0CAD2F569FFDE".hexStringToByteArray(),
                                     data=null),
                            TxDetail(rid="C7843B794006D94305DEABBB5B211B1A3DF76A953F5535CD84750266DDDF54DA".hexStringToByteArray(),
                                     hash="A2A5A9D10DB3EBE135E8DB3335B52F02839050ED0A485D2BA612B9827510EB29".hexStringToByteArray(),
                                     data=null)
                    )
                }
    }

    @Test
    fun testWaitOnShutdown() {
        val countDownLatch = CountDownLatch(1)
        val mockDelayModule: GTXModule = mock()
        whenever(mockDelayModule.query(any(), any(), any())).thenAnswer {
            countDownLatch.countDown()
            Thread.sleep(500)
            GtvString("dummy")
        }
        val mockStorage: Storage = mock {
            on { openReadConnection(0) } doReturn mock()
        }

        val blockQueries = GTXBlockQueries(mock(), mockStorage, mock(), 0, ByteArray(0), mockDelayModule, mock())

        val executorService = Executors.newSingleThreadExecutor()

        executorService.submit {
            blockQueries.query("dummy", GtvNull)
        }

        countDownLatch.await()
        val timeInMillis = measureTimeMillis {
            blockQueries.shutdown()
        }

        assertThat(timeInMillis).isBetween(400, 600)
    }
}

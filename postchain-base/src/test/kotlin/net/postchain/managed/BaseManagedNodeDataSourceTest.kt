package net.postchain.managed

import net.postchain.base.PeerInfo
import net.postchain.common.BlockchainRid
import net.postchain.common.BlockchainRid.Companion.ZERO_RID
import net.postchain.common.wrap
import net.postchain.config.app.AppConfig
import net.postchain.core.NodeRid
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.managed.query.QueryRunner
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import java.time.Instant
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class BaseManagedNodeDataSourceTest {

    @ParameterizedTest
    @MethodSource("getPeerInfosTestData")
    fun testGetPeerInfos(gtvResult: Gtv, expected: Array<PeerInfo>) {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_get_peer_infos"), any()) } doReturn gtvResult
        }
        val sut = BaseManagedNodeDataSource(queryRunner, mock())
        assertContentEquals(expected, sut.getPeerInfos())
    }

    @ParameterizedTest
    @MethodSource("computeBlockchainInfoListTestData")
    fun testComputeBlockchainInfoList(gtvResult: Gtv, expected: List<BlockchainInfo>) {
        val appConfig: AppConfig = mock {
            on { pubKeyByteArray } doReturn byteArrayOf(0)
        }
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(4)
            on { query(eq("nm_compute_blockchain_info_list"), any()) } doReturn gtvResult
        }
        val sut = BaseManagedNodeDataSource(queryRunner, appConfig)
        assertEquals(expected.sortedBy { it.rid.toHex() }, sut.computeBlockchainInfoList().sortedBy { it.rid.toHex() })
    }

    @ParameterizedTest
    @MethodSource("getConfigurationTestData")
    fun testGetConfiguration(gtvResult: Gtv, expected: ByteArray?) {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_get_blockchain_configuration"), any()) } doReturn gtvResult
        }
        val sut = BaseManagedNodeDataSource(queryRunner, mock())
        assertEquals(expected?.wrap(), sut.getConfiguration(ZERO_RID.data, 0L)?.wrap())
    }

    @ParameterizedTest
    @MethodSource("findNextConfigurationHeightTestData")
    fun testFindNextConfigurationHeight(gtvResult: Gtv, expected: Long?) {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_find_next_configuration_height"), any()) } doReturn gtvResult
        }
        val sut = BaseManagedNodeDataSource(queryRunner, mock())
        assertEquals(expected, sut.findNextConfigurationHeight(ZERO_RID.data, 0L))
    }

    @ParameterizedTest
    @MethodSource("syncUntilHeightTestData")
    fun testGetSyncUntilHeight(bcInfos: List<BlockchainInfo>, gtvResult: Gtv, expected: Map<BlockchainRid, Long>) {
        val appConfig: AppConfig = mock {
            on { pubKeyByteArray } doReturn byteArrayOf(0)
        }
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_get_blockchain_last_height_map"), any()) } doReturn gtvResult
        }
        val sut = spy(BaseManagedNodeDataSource(queryRunner, appConfig))
        // Traditional kotlin stubbing block { } will call stubbed function, which results in NPE
        Mockito.doReturn(4).`when`(sut).nmApiVersion
        Mockito.doReturn(bcInfos).`when`(sut).computeBlockchainInfoList()

        println(expected)
        println(sut.getSyncUntilHeight())
        assertEquals(expected, sut.getSyncUntilHeight())
    }

    @ParameterizedTest
    @MethodSource("blockchainReplicaNodeMapTestData")
    fun testGetBlockchainReplicaNodeMap(gtvResult: Gtv, expected: Map<BlockchainRid, List<NodeRid>>) {
        val appConfig: AppConfig = mock {
            on { pubKeyByteArray } doReturn byteArrayOf(0)
        }
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(4)
            on { query(eq("nm_compute_blockchain_info_list"), any()) } doReturn GtvArray(emptyArray())
            on { query(eq("nm_get_blockchain_replica_node_map_v4"), any()) } doReturn gtvResult
        }
        val sut = BaseManagedNodeDataSource(queryRunner, appConfig)
        assertEquals(expected, sut.getBlockchainReplicaNodeMap())
    }

    companion object {

        @JvmStatic
        fun getPeerInfosTestData(): List<Array<Any>> {
            val nonTrivialGtv = gtv(listOf(
                    gtv(gtv("host1"), gtv(5555), gtv(byteArrayOf(1)), gtv(1000)),
                    gtv(gtv("host2"), gtv(7777), gtv(byteArrayOf(2)), gtv(2000)),
            ))
            val nonTrivialExpected = arrayOf(
                    PeerInfo("host1", 5555, byteArrayOf(1), Instant.ofEpochSecond(1)),
                    PeerInfo("host2", 7777, byteArrayOf(2), Instant.ofEpochSecond(2))
            )

            return listOf(
                    arrayOf(GtvArray(emptyArray()), arrayOf<PeerInfo>()),
                    arrayOf(nonTrivialGtv, nonTrivialExpected)
            )
        }

        @JvmStatic
        fun computeBlockchainInfoListTestData(): List<Array<Any>> {
            val brid0 = ZERO_RID
            val brid1 = BlockchainRid.buildRepeat(1)
            val nonTrivialGtv = GtvArray(arrayOf(
                    gtv(mapOf("rid" to gtv(brid0), "system" to gtv(true))),
                    gtv(mapOf("rid" to gtv(brid1), "system" to gtv(false)))
            ))
            val nonTrivialExpected = listOf(
                    BlockchainInfo(brid0, true),
                    BlockchainInfo(brid1, false)
            )

            return listOf(
                    arrayOf(GtvArray(emptyArray()), emptyList<BlockchainInfo>()),
                    arrayOf(nonTrivialGtv, nonTrivialExpected)
            )
        }

        @JvmStatic
        fun getConfigurationTestData(): List<Array<Any?>> {
            return listOf(
                    arrayOf(GtvNull, null),
                    arrayOf(gtv(byteArrayOf(1, 2, 3)), byteArrayOf(1, 2, 3))
            )
        }

        @JvmStatic
        fun findNextConfigurationHeightTestData(): List<Array<Any?>> {
            return listOf(
                    arrayOf(GtvNull, null),
                    arrayOf(gtv(10), 10L)
            )
        }

        @JvmStatic
        fun syncUntilHeightTestData(): List<Array<Any>> {
            val emptyParamSet = arrayOf(
                    listOf<BlockchainInfo>(),
                    GtvArray(emptyArray()),
                    emptyMap<BlockchainRid, Long>()
            )

            val bc1Info = BlockchainInfo(ZERO_RID, true)
            val bc2Info = BlockchainInfo(BlockchainRid.buildRepeat(1), false)
            val goodParamSet = arrayOf(
                    listOf(bc1Info, bc2Info),
                    GtvArray(arrayOf(gtv(123), gtv(456))),
                    mapOf(
                            bc1Info.rid to 123L,
                            bc2Info.rid to 456L
                    )
            )

            val truncatedParamSet = arrayOf(
                    listOf(bc1Info, bc2Info),
                    GtvArray(arrayOf(gtv(123))),
                    mapOf(
                            bc1Info.rid to 123L,
                            bc2Info.rid to -1L
                    )
            )

            val extendedParamSet = arrayOf(
                    listOf(bc1Info, bc2Info),
                    GtvArray(arrayOf(gtv(123), gtv(456), gtv(789))),
                    mapOf(
                            bc1Info.rid to 123L,
                            bc2Info.rid to 456L
                    )
            )

            return listOf(emptyParamSet, goodParamSet, truncatedParamSet, extendedParamSet)
        }

        @JvmStatic
        fun blockchainReplicaNodeMapTestData(): List<Array<Any>> {
            val brid0 = BlockchainRid.buildRepeat(0)
            val brid1 = BlockchainRid.buildRepeat(1)
            val node0 = NodeRid(byteArrayOf(10))
            val node1 = NodeRid(byteArrayOf(20))
            val node2 = NodeRid(byteArrayOf(30))

            val emptyGtvResult = GtvArray(emptyArray())
            val emptyExpected = mapOf<BlockchainRid, List<NodeRid>>()

            val gtvResult = gtv(listOf(
                    gtv(listOf(
                            gtv(brid0.data),
                            gtv(listOf(gtv(node0.data), gtv(node1.data)))
                    )),
                    gtv(listOf(
                            gtv(brid1.data),
                            gtv(listOf(gtv(node0.data), gtv(node1.data), gtv(node2.data)))
                    ))
            ))
            val expected = mapOf(
                    brid0 to listOf(node0, node1),
                    brid1 to listOf(node0, node1, node2)
            )

            return listOf(
                    arrayOf(emptyGtvResult, emptyExpected),
                    arrayOf(gtvResult, expected)
            )
        }
    }
}
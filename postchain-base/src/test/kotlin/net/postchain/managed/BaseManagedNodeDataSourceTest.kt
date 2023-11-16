package net.postchain.managed

import assertk.assertThat
import assertk.isContentEqualTo
import net.postchain.base.PeerInfo
import net.postchain.base.configuration.BlockchainConfigurationOptions
import net.postchain.base.configuration.KEY_SIGNERS
import net.postchain.common.BlockchainRid
import net.postchain.common.BlockchainRid.Companion.ZERO_RID
import net.postchain.common.wrap
import net.postchain.config.app.AppConfig
import net.postchain.core.BlockchainState
import net.postchain.core.NodeRid
import net.postchain.crypto.PubKey
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.managed.query.QueryRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.time.Instant

class BaseManagedNodeDataSourceTest {

    @ParameterizedTest
    @MethodSource("getPeerInfosTestData")
    fun testGetPeerInfos(gtvResult: Gtv, expected: Array<PeerInfo>) {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_get_peer_infos"), any()) } doReturn gtvResult
        }
        val sut = BaseManagedNodeDataSource(queryRunner, mock())
        assertThat(sut.getPeerInfos()).isContentEqualTo(expected)
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
    @MethodSource("blockchainReplicaNodeMapTestData")
    fun testGetBlockchainReplicaNodeMap(gtvResult: Gtv, expected: Map<BlockchainRid, List<NodeRid>>) {
        val appConfig: AppConfig = mock {
            on { pubKeyByteArray } doReturn byteArrayOf(0)
        }
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(4)
            on { query(eq("nm_compute_blockchain_info_list"), any()) } doReturn GtvArray(emptyArray())
            on { query(eq("nm_get_blockchain_replica_node_map"), any()) } doReturn gtvResult
        }
        val sut = BaseManagedNodeDataSource(queryRunner, appConfig)
        assertEquals(expected, sut.getBlockchainReplicaNodeMap())
    }

    @ParameterizedTest
    @MethodSource("getPendingBlockchainConfigurationTestData")
    fun testGetPendingBlockchainConfiguration(gtvResult: Gtv, expected: List<PendingBlockchainConfiguration>) {
        val appConfig: AppConfig = mock {
            on { pubKeyByteArray } doReturn byteArrayOf(0)
            on { cryptoSystem } doReturn Secp256K1CryptoSystem()
        }
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(5)
            on { query(eq("nm_get_pending_blockchain_configuration"), any()) } doReturn gtvResult
        }
        val sut = BaseManagedNodeDataSource(queryRunner, appConfig)
        assertEquals(expected, sut.getPendingBlockchainConfiguration(ZERO_RID, 0L))
    }

    @ParameterizedTest
    @MethodSource("getBlockchainStateTestData")
    fun testGetBlockchainState(gtvResult: Gtv, expected: BlockchainState) {
        val appConfig: AppConfig = mock {
            on { pubKeyByteArray } doReturn byteArrayOf(0)
        }
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(6)
            on { query(eq("nm_get_blockchain_state"), any()) } doReturn gtvResult
        }
        val sut = BaseManagedNodeDataSource(queryRunner, appConfig)
        assertEquals(expected, sut.getBlockchainState(ZERO_RID))
    }

    @ParameterizedTest
    @MethodSource("getBlockchainConfigurationOptionsData")
    fun testGetBlockchainConfigurationOptions(gtvResult: Gtv, expected: BlockchainConfigurationOptions?) {
        val appConfig: AppConfig = mock {
            on { pubKeyByteArray } doReturn byteArrayOf(0)
        }
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(8)
            on { query(eq("nm_get_blockchain_configuration_options"), any()) } doReturn gtvResult
        }
        val sut = BaseManagedNodeDataSource(queryRunner, appConfig)
        assertEquals(expected, sut.getBlockchainConfigurationOptions(ZERO_RID, 0L))
    }

    @ParameterizedTest
    @MethodSource("findNextInactiveBlockchainsData")
    fun testFindNextInactiveBlockchains(gtvResult: Gtv, expected: List<InactiveBlockchainInfo>?) {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(11)
            on { query(eq("nm_find_next_inactive_blockchains"), any()) } doReturn gtvResult
        }
        val sut = BaseManagedNodeDataSource(queryRunner, mock())
        assertEquals(expected, sut.findNextInactiveBlockchains(0L))
    }

    @ParameterizedTest
    @MethodSource("testGetUnarchivingBlockchainInfoData")
    fun testGetUnarchivingBlockchainInfo(apiVersion: Long, gtvResult: Gtv, expected: UnarchivingBlockchainInfo?) {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(apiVersion)
            on { query(eq("nm_get_unarchiving_blockchain_info"), any()) } doReturn gtvResult
        }
        val sut = BaseManagedNodeDataSource(queryRunner, mock())
        assertEquals(expected, sut.getUnarchivingBlockchainInfo(ZERO_RID))
    }


    companion object {

        private val hashCalculator: GtvMerkleHashCalculator = GtvMerkleHashCalculator(Secp256K1CryptoSystem())

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
                    BlockchainInfo(brid0, true, BlockchainState.RUNNING),
                    BlockchainInfo(brid1, false, BlockchainState.RUNNING)
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

        @JvmStatic
        fun getPendingBlockchainConfigurationTestData(): List<Array<Any?>> {
            val pubKey0 = PubKey(ByteArray(33) { 0 })

            val baseConfig0: Gtv = gtv(mapOf())
            val encodedConfig0 = GtvEncoder.encodeGtv(baseConfig0)
            val gtvResult0 = gtv(mapOf(
                    "base_config" to gtv(encodedConfig0),
                    "signers" to gtv(listOf(gtv(pubKey0.data))),
                    "minimum_height" to gtv(5)
            ))

            val fullConfig = baseConfig0.asDict().toMutableMap()
            fullConfig[KEY_SIGNERS] = gtv(listOf(gtv(pubKey0.data)))
            val expected0 = PendingBlockchainConfiguration(
                    baseConfig0,
                    gtv(fullConfig).merkleHash(hashCalculator).wrap(),
                    listOf(pubKey0),
                    5
            )

            return listOf(
                    arrayOf(gtv(listOf()), listOf<PendingBlockchainConfiguration>()),
                    arrayOf(gtv(listOf(gtvResult0)), listOf(expected0))
            )
        }

        @JvmStatic
        fun getBlockchainStateTestData(): List<Array<Any?>> {
            return listOf(
                    arrayOf(gtv("RUNNING"), BlockchainState.RUNNING),
                    arrayOf(gtv("PAUSED"), BlockchainState.PAUSED)
            )
        }

        @JvmStatic
        fun getBlockchainConfigurationOptionsData(): List<Array<Any?>> {
            return listOf(
                    arrayOf(GtvNull, null),
                    arrayOf(
                            gtv(mapOf("suppress_special_transaction_validation" to gtv(true))),
                            BlockchainConfigurationOptions(true)
                    ),
                    arrayOf(
                            gtv(mapOf("suppress_special_transaction_validation" to gtv(false))),
                            BlockchainConfigurationOptions(false)
                    )
            )
        }

        @JvmStatic
        fun findNextInactiveBlockchainsData(): List<Array<Any?>> {
            val brid0 = ZERO_RID
            val brid1 = BlockchainRid.buildRepeat(1)

            val nonTrivialGtv = GtvArray(arrayOf(
                    gtv(mapOf("rid" to gtv(brid0), "state" to gtv("REMOVED"), "height" to gtv(10))),
                    gtv(mapOf("rid" to gtv(brid1), "state" to gtv("ARCHIVED"), "height" to gtv(20)))
            ))
            val nonTrivialExpected = listOf(
                    InactiveBlockchainInfo(brid0, BlockchainState.REMOVED, 10),
                    InactiveBlockchainInfo(brid1, BlockchainState.ARCHIVED, 20)
            )

            return listOf(
                    arrayOf(GtvArray(emptyArray()), emptyList<InactiveBlockchainInfo>()),
                    arrayOf(nonTrivialGtv, nonTrivialExpected)
            )
        }

        @JvmStatic
        fun testGetUnarchivingBlockchainInfoData(): List<Array<Any?>> {
            return listOf(
                    arrayOf(12, GtvNull, null),
                    arrayOf(13,
                            gtv(mapOf("rid" to gtv(ZERO_RID), "source_container" to gtv("src"), "destination_container" to gtv("dst"), "up_to_height" to gtv(100))),
                            UnarchivingBlockchainInfo(ZERO_RID, "src", "dst", 100L)
                    ),
            )
        }
    }
}

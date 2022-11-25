package net.postchain.managed

import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.core.NodeRid
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.managed.query.QueryRunner
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import kotlin.test.assertEquals

class BaseManagedNodeDataSourceTest {

    @ParameterizedTest
    @MethodSource("testData")
    fun testGetBlockchainReplicaNodeMap(gtvResult: Gtv, expected: Map<BlockchainRid, List<NodeRid>>) {
        val appConfig: AppConfig = mock {
            on { pubKeyByteArray } doReturn byteArrayOf(0)
        }
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_compute_blockchain_list"), any()) } doReturn GtvArray(emptyArray())
            on { query(eq("nm_get_blockchain_replica_node_map_v4"), any()) } doReturn gtvResult
        }
        val sut = BaseManagedNodeDataSource(queryRunner, appConfig)
        assertEquals(expected, sut.getBlockchainReplicaNodeMap())
    }

    companion object {
        @JvmStatic
        fun testData(): List<Array<Any>> {
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
package net.postchain.managed

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.isContentEqualTo
import net.postchain.common.BlockchainRid.Companion.ZERO_RID
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.ContainerImageInfo
import net.postchain.containers.bpm.ContainerResourceLimits
import net.postchain.containers.bpm.resources.Cpu
import net.postchain.containers.bpm.resources.IoRead
import net.postchain.containers.bpm.resources.IoWrite
import net.postchain.containers.bpm.resources.Ram
import net.postchain.containers.bpm.resources.ResourceLimitType
import net.postchain.containers.bpm.resources.Storage
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.managed.query.QueryRunner
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

class BaseDirectoryDataSourceTest {

    private val appConfig: AppConfig = mock {
        on { pubKeyByteArray } doReturn byteArrayOf(0)
    }

    @ParameterizedTest
    @MethodSource("getContainersToRunTestData")
    fun testGetContainersToRun(gtvResult: Gtv, expected: Array<String>) {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_get_containers"), any()) } doReturn gtvResult
        }
        val sut = BaseDirectoryDataSource(queryRunner, appConfig)
        assertThat(sut.getContainersToRun()?.toTypedArray() ?: emptyArray()).isContentEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource("getContainerForBlockchainTestData")
    fun testGetContainerForBlockchain(gtvResult: Gtv, expected: String) {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(3)
            on { query(eq("nm_get_container_for_blockchain"), any()) } doReturn gtvResult
        }
        val sut = BaseDirectoryDataSource(queryRunner, appConfig)
        assertThat(sut.getContainerForBlockchain(ZERO_RID)).isEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource("getBlockchainContainersForNodeTestData")
    fun testGetBlockchainContainersForNode(apiVersion: Long, gtvResult: Gtv, expected: List<String>) {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(apiVersion)
            on { query(eq("nm_get_container_for_blockchain"), any()) } doReturn (gtvResult.asArray().firstOrNull()
                    ?: gtv("unreachable state"))
            on { query(eq("nm_get_blockchain_containers_for_node"), any()) } doReturn gtvResult
        }
        val sut = BaseDirectoryDataSource(queryRunner, appConfig)
        assertThat(sut.getBlockchainContainersForNode(ZERO_RID)).isEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource("getResourceLimitForContainerTestData")
    fun testGetResourceLimitForContainer(gtvResult: Gtv, expected: ContainerResourceLimits) {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_get_container_limits"), any()) } doReturn gtvResult
        }
        val sut = BaseDirectoryDataSource(queryRunner, mock())
        assertThat(sut.getResourceLimitForContainer("my_container")).isEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource("getImageForContainerTestData")
    fun testGetImageForContainer(apiVersion: Long, gtvResult: Gtv, expected: ContainerImageInfo?) {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(apiVersion)
            on { query(eq("nm_get_container_image"), any()) } doReturn gtvResult
        }
        val sut = BaseDirectoryDataSource(queryRunner, appConfig)
        assertThat(sut.getImageForContainer("my_container")).isEqualTo(expected)
    }

    companion object {

        @JvmStatic
        fun getContainersToRunTestData(): List<Array<Any>> = listOf(
                arrayOf(gtv(emptyList()), arrayOf<String>()),
                arrayOf(gtv(gtv("foo"), gtv("bar")), arrayOf("foo", "bar")),
        )

        @JvmStatic
        fun getContainerForBlockchainTestData(): List<Array<Any>> = listOf(
                arrayOf(gtv("foo"), "foo")
        )

        @JvmStatic
        fun getBlockchainContainersForNodeTestData(): List<Array<Any>> = listOf(
                arrayOf(13, gtv(listOf(gtv("foo"))), listOf("foo")),
                arrayOf(14, GtvArray(arrayOf()), listOf<String>()),
                arrayOf(14, gtv(listOf(gtv("foo"))), listOf("foo")),
                arrayOf(14, gtv(listOf(gtv("foo"), gtv("bar"))), listOf("foo", "bar")),
        )

        @JvmStatic
        fun getResourceLimitForContainerTestData(): List<Array<Any>> = listOf(
                arrayOf(gtv(emptyMap()), ContainerResourceLimits.default()),
                arrayOf(
                        gtv("cpu" to gtv(50)),
                        ContainerResourceLimits(mapOf(
                                ResourceLimitType.CPU to Cpu(50)
                        ))
                ),
                arrayOf(
                        gtv(
                                "cpu" to gtv(50),
                                "ram" to gtv(2048),
                                "storage" to gtv(16384),
                                "io_read" to gtv(25),
                                "io_write" to gtv(20),
                        ),
                        ContainerResourceLimits(mapOf(
                                ResourceLimitType.CPU to Cpu(50),
                                ResourceLimitType.RAM to Ram(2048),
                                ResourceLimitType.STORAGE to Storage(16384),
                                ResourceLimitType.IO_READ to IoRead(25),
                                ResourceLimitType.IO_WRITE to IoWrite(20),
                        ))
                ),
        )

        @JvmStatic
        fun getImageForContainerTestData(): List<Array<Any?>> = listOf(
                arrayOf(19, GtvNull, null),
                arrayOf(
                        20,
                        gtv(mapOf(
                                "name" to gtv("image_name"),
                                "url" to gtv("image_url"),
                                "digest" to gtv("image_digest"),
                        )),
                        ContainerImageInfo("image_name", "image_url", "image_digest")
                )
        )

    }
}

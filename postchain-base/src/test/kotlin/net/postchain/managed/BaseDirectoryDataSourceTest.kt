package net.postchain.managed

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.isContentEqualTo
import net.postchain.common.BlockchainRid.Companion.ZERO_RID
import net.postchain.common.exception.UserMistake
import net.postchain.config.app.AppConfig
import net.postchain.containers.ContainerRateLimit
import net.postchain.containers.bpm.ContainerImageInfo
import net.postchain.containers.bpm.ContainerJarExtensionInfo
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
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

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

    @Test
    fun testGetContainersToRunUserMistake() {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_get_containers"), any()) } doThrow
                    UserMistake("Node not found: ${ZERO_RID.wData}")
        }
        val sut = BaseDirectoryDataSource(queryRunner, appConfig)
        assertThat(sut.getContainersToRun() ?: listOf()).isEmpty()
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

    @Test
    fun testGetContainerForBlockchainUserMistake() {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(3)
            on { query(eq("nm_get_container_for_blockchain"), any()) } doThrow
                    UserMistake("Node not found: ${ZERO_RID.wData}")
        }
        val sut = BaseDirectoryDataSource(queryRunner, appConfig)
        assertThrows<Exception> {
            sut.getContainerForBlockchain(ZERO_RID)
        }
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

    @Test
    fun testGetBlockchainContainersForNodeUserMistake() {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(14)
            on { query(eq("nm_get_blockchain_containers_for_node"), any()) } doThrow
                    UserMistake("Node not found: ${ZERO_RID.wData}")
        }
        val sut = BaseDirectoryDataSource(queryRunner, appConfig)
        assertThat(sut.getBlockchainContainersForNode(ZERO_RID)).isEmpty()
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

    @Test
    fun testGetResourceLimitForContainerUserMistake() {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_get_container_limits"), any()) } doThrow
                    UserMistake("Container my_container not found")
        }
        val sut = BaseDirectoryDataSource(queryRunner, mock())
        assertEquals(ContainerResourceLimits.default(), sut.getResourceLimitForContainer("my_container"))
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

    @Test
    fun testGetImageForContainerUserMistake() {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(20)
            on { query(eq("nm_get_container_image"), any()) } doThrow
                    UserMistake("Container my_container not found")
        }
        val sut = BaseDirectoryDataSource(queryRunner, appConfig)
        assertThat(sut.getImageForContainer("my_container")).isEqualTo(null)
    }

    @ParameterizedTest
    @MethodSource("getImageForContainerOrDefaultTestData")
    fun testGetImageForContainerOrDefault(apiVersion: Long, gtvResult1: Gtv, gtvResult2: Gtv, expected: ContainerImageInfo?) {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(apiVersion)
            on { query(eq("nm_get_container_image"), any()) } doReturn gtvResult1
            on { query(eq("nm_get_container_image_or_default"), any()) } doReturn gtvResult2
        }
        val sut = BaseDirectoryDataSource(queryRunner, appConfig)
        assertThat(sut.getImageForContainerOrDefault("my_container")).isEqualTo(expected)
    }

    @Test
    fun testGetImageForContainerOrDefaultUserMistake() {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(22)
            on { query(eq("nm_get_container_image_or_default"), any()) } doThrow
                    UserMistake("Container my_container not found")
        }
        val sut = BaseDirectoryDataSource(queryRunner, appConfig)
        assertThat(sut.getImageForContainerOrDefault("my_container")).isEqualTo(null)
    }

    @ParameterizedTest
    @MethodSource("getContainerCreationTimeTestData")
    fun testGetContainerCreationTime(gtvResult: Gtv, expected: Instant?) {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(23)
            on { query(eq("nm_get_container_creation_time"), any()) } doReturn gtvResult
        }
        val sut = BaseDirectoryDataSource(queryRunner, appConfig)
        assertThat(sut.getContainerCreationTime("my_container")).isEqualTo(expected)
    }

    @Test
    fun testGetContainerCreationTimeUserMistake() {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(23)
            on { query(eq("nm_get_container_creation_time"), any()) } doThrow
                    UserMistake("Container my_container not found")
        }
        val sut = BaseDirectoryDataSource(queryRunner, appConfig)
        assertThat(sut.getContainerCreationTime("my_container")).isEqualTo(null)
    }

    @ParameterizedTest
    @MethodSource("getExtensionRateLimitsTestData")
    fun testGetExtensionRateLimits(gtvResult: Gtv, expected: Map<String, ContainerRateLimit>) {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(23)
            on { query(eq("nm_get_container_rate_limits"), any()) } doReturn gtvResult
        }
        val sut = BaseDirectoryDataSource(queryRunner, appConfig)
        assertThat(sut.getContainerRateLimits("my_container")).isEqualTo(expected)
    }

    @Test
    fun testGetExtensionRateLimitsUserMistake() {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(23)
            on { query(eq("nm_get_container_rate_limits"), any()) } doThrow
                    UserMistake("Container my_container not found")
        }
        val sut = BaseDirectoryDataSource(queryRunner, appConfig)
        assertThat(sut.getContainerRateLimits("my_container")).isEmpty()
    }

    @ParameterizedTest
    @MethodSource("getJarExtensionsForContainerTestData")
    fun testGetJarExtensionsForContainer(apiVersion: Long, gtvResult: Gtv, expected: List<ContainerJarExtensionInfo>) {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(apiVersion)
            on { query(eq("nm_get_container_jar_extensions"), any()) } doReturn gtvResult
        }
        val sut = BaseDirectoryDataSource(queryRunner, appConfig)
        assertThat(sut.getJarExtensionsForContainer("my_container")).isEqualTo(expected)
    }

    @Test
    fun testGetJarExtensionsForContainerUserMistake() {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(27)
            on { query(eq("nm_get_container_jar_extensions"), any()) } doThrow
                    UserMistake("Container my_container not found")
        }
        val sut = BaseDirectoryDataSource(queryRunner, appConfig)
        assertThat(sut.getJarExtensionsForContainer("my_container")).isEmpty()
    }

    @ParameterizedTest
    @MethodSource("getJarExtensionTestData")
    fun testGetJarExtension(apiVersion: Long, gtvResult: Gtv, expected: ByteArray?) {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(apiVersion)
            on { query(eq("nm_get_jar_extension"), any()) } doReturn gtvResult
        }
        val sut = BaseDirectoryDataSource(queryRunner, appConfig)
        val actual = sut.getJarExtension("ext_1")
        if (expected == null) {
            assertThat(actual).isEqualTo(null)
        } else {
            assertThat(actual?.toList()).isEqualTo(expected.toList())
        }
    }

    @Test
    fun testGetJarExtensionUserMistake() {
        val queryRunner: QueryRunner = mock {
            on { query(eq("nm_api_version"), any()) } doReturn gtv(27)
            on { query(eq("nm_get_jar_extension"), any()) } doThrow
                    UserMistake("Extension ext_1 not found")
        }
        val sut = BaseDirectoryDataSource(queryRunner, appConfig)
        assertThat(sut.getJarExtension("ext_1")).isEqualTo(null)
    }

    companion object {

        @JvmStatic
        fun getContainersToRunTestData(): List<Array<Any>> = listOf(
                arrayOf(gtv(emptyList()), arrayOf<String>()),
                arrayOf(gtv(gtv("foo"), gtv("bar")), arrayOf("foo", "bar")),
        )

        @JvmStatic
        fun getContainersToRunUserMistakeTestData(): List<Array<Any>> = listOf(
                arrayOf(UserMistake("Test error"), arrayOf<String>())
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

        @JvmStatic
        fun getImageForContainerOrDefaultTestData(): List<Array<Any?>> = listOf(
                arrayOf(19, GtvNull, GtvNull, null),
                arrayOf(
                        20,
                        gtv(mapOf(
                                "name" to gtv("image_name"),
                                "url" to gtv("image_url"),
                                "digest" to gtv("image_digest"),
                        )),
                        GtvNull,
                        ContainerImageInfo("image_name", "image_url", "image_digest")
                ),
                arrayOf(
                        22,
                        GtvNull,
                        gtv(mapOf(
                                "name" to gtv("image_name"),
                                "url" to gtv("image_url"),
                                "digest" to gtv("image_digest"),
                        )),
                        ContainerImageInfo("image_name", "image_url", "image_digest")
                )
        )

        @JvmStatic
        fun getContainerCreationTimeTestData(): List<Array<Any?>> = listOf(
                arrayOf(GtvNull, null),
                arrayOf(gtv(1234567890L), Instant.ofEpochMilli(1234567890L))
        )

        @JvmStatic
        fun getExtensionRateLimitsTestData(): List<Array<Any>> = listOf(
                arrayOf(
                        gtv(mapOf(
                                "ext_1" to gtv(mapOf(
                                        "period_length_millis" to gtv(1000),
                                        "rate_limit" to gtv(50),
                                )),
                                "ext_2" to gtv(mapOf(
                                        "period_length_millis" to gtv(2000),
                                        "rate_limit" to gtv(100),
                                )),
                        )),
                        mapOf(
                                "ext_1" to ContainerRateLimit(1000.milliseconds, 50),
                                "ext_2" to ContainerRateLimit(2000.milliseconds, 100)
                        )
                ),
                arrayOf(
                        gtv(emptyMap()),
                        emptyMap<String, ContainerRateLimit>()
                )
        )

        @JvmStatic
        fun getJarExtensionsForContainerTestData(): List<Array<Any?>> = listOf(
                arrayOf(
                        26L,
                        GtvArray(arrayOf()),
                        emptyList<ContainerJarExtensionInfo>()
                ),
                arrayOf(
                        27L,
                        GtvArray(arrayOf()),
                        emptyList<ContainerJarExtensionInfo>()
                ),
                arrayOf(
                        27L,
                        gtv(listOf(
                                gtv(mapOf(
                                        "name" to gtv("ext_1"),
                                        "hash" to gtv(byteArrayOf(1, 2, 3))
                                )),
                                gtv(mapOf(
                                        "name" to gtv("ext_2"),
                                        "hash" to gtv(byteArrayOf(4, 5, 6))
                                )),
                        )),
                        listOf(
                                ContainerJarExtensionInfo("ext_1", byteArrayOf(1, 2, 3)),
                                ContainerJarExtensionInfo("ext_2", byteArrayOf(4, 5, 6))
                        )
                )
        )

        @JvmStatic
        fun getJarExtensionTestData(): List<Array<Any?>> = listOf(
                arrayOf(26L, gtv(byteArrayOf(9, 9)), null),
                arrayOf(27L, gtv(byteArrayOf(7, 8, 9)), byteArrayOf(7, 8, 9))
        )
    }
}

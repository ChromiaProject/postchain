package net.postchain.api.rest.endpoint

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.restassured.RestAssured
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.common.BlockchainRid
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvFileReader
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Paths

class RestApiConfigFeaturesTest {

    private val basePath = "/api/v1"
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")

    private lateinit var bccFile: File
    private lateinit var bccByteArray: ByteArray
    private lateinit var restApi: RestApi
    private lateinit var model: Model

    @BeforeEach
    fun setup() {
        model = mock {
            on { chainIID } doReturn 1L
            on { blockchainRid } doReturn blockchainRID
            on { live } doReturn true
        }

        bccFile = Paths.get(javaClass.getResource("/net/postchain/config/blockchain_config.xml")!!.toURI()).toFile()
        bccByteArray = GtvEncoder.encodeGtv(GtvFileReader.readFile(bccFile))

        restApi = RestApi(0, basePath, gracefulShutdown = false)
    }

    @AfterEach
    fun tearDown() {
        restApi.close()
    }

    @Test
    fun `Configuration features empty`() {
        val height = -1L
        whenever(model.getBlockchainConfiguration(height))
                .thenReturn(GtvEncoder.encodeGtv(gtv(emptyMap())))

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .queryParam("height", height)
                .get("/config/$blockchainRID/features")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo("""{}"""))
    }

    @Test
    fun `Configuration features at current height`() {
        val height = -1L
        whenever(model.getBlockchainConfiguration(height)).thenReturn(bccByteArray)

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .queryParam("height", height)
                .get("/config/$blockchainRID/features")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo("""{"array_feature":[10,20],"merkle_hash_version":2}"""))
    }

    @Test
    fun `Configuration features at height`() {
        val height = 3L
        whenever(model.getBlockchainConfiguration(height)).thenReturn(bccByteArray)

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .queryParam("height", height)
                .get("/config/$blockchainRID/features")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo("""{"array_feature":[10,20],"merkle_hash_version":2}"""))
    }

    @Test
    fun `Configuration features at height with binary response`() {
        val height = 3L
        whenever(model.getBlockchainConfiguration(height)).thenReturn(bccByteArray)

        restApi.attachModel(blockchainRID, model)

        val response = RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.BINARY)
                .queryParam("height", height)
                .get("/config/$blockchainRID/features")
                .then()
                .statusCode(200)
                .contentType(ContentType.BINARY)

        val gtv = GtvDecoder.decodeGtv(response.extract().body().asByteArray()).asDict()
        assertThat(gtv["merkle_hash_version"]?.asInteger()).isEqualTo(2)
        assertThat(gtv["array_feature"])
                .isEqualTo(gtv(listOf(gtv(10), gtv(20))))
    }
}

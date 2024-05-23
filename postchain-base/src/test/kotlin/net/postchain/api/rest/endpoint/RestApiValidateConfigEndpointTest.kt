package net.postchain.api.rest.endpoint

import io.restassured.RestAssured
import io.restassured.http.ContentType
import net.postchain.PostchainContext
import net.postchain.api.rest.X_POSTCHAIN_SIGNATURE_HEADER
import net.postchain.api.rest.controller.PostchainModel
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.controller.UNAUTHORIZED_CONF_NOT_SIGNED_BY_PROVIDER
import net.postchain.api.rest.controller.UNAUTHORIZED_INVALID_SIGNATURE
import net.postchain.api.rest.controller.UNAUTHORIZED_REQUIRE_SIGNATURE_IN_MANAGED_MODE
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.BlockchainConfiguration
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.devtools.KeyPairHelper.privKey
import net.postchain.crypto.devtools.KeyPairHelper.pubKey
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvFileReader
import net.postchain.gtv.gtvml.GtvMLEncoder
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.managed.ManagedNodeDataSource
import net.postchain.managed.config.ManagedBlockchainConfiguration
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.startsWith
import org.hamcrest.core.IsEqual.equalTo
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Paths
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RestApiValidateConfigEndpointTest {

    private val basePath = "/api/v1"
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")

    private lateinit var restApi: RestApi
    private lateinit var model: PostchainModel
    private lateinit var dataSource: ManagedNodeDataSource

    private val cryptoSystem = Secp256K1CryptoSystem()
    private val config = GtvFileReader.readFile(bccFile)
    private val keyPair0 = KeyPair(pubKey(0), privKey(0))
    private val sigMaker0 = cryptoSystem.buildSigMaker(keyPair0)

    @BeforeEach
    fun setup() {
        setManagedModel()

        restApi = RestApi(0, basePath, gracefulShutdown = false, clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
    }

    @AfterEach
    fun tearDown() {
        restApi.close()
    }

    @Test
    fun `Validate configuration endpoint can parse XML`() {
        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Content-Type", "text/xml")
                .body(buildXmlBody(config))
                .header(X_POSTCHAIN_SIGNATURE_HEADER, buildValidHeaderAuth(config))
                .post("/config/$blockchainRID")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo("{}"))
    }

    @Test
    fun `Validate configuration endpoint can decode GTV`() {
        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Content-Type", "application/octet-stream")
                .body(buildByteBody(config))
                .header(X_POSTCHAIN_SIGNATURE_HEADER, buildValidHeaderAuth(config))
                .post("/config/$blockchainRID")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo("{}"))
    }

    @Test
    fun `Validate configuration endpoint can return 400 on invalid XML`() {
        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Content-Type", "text/xml")
                .body("no XML")
                .post("/config/$blockchainRID")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", containsString(""))
    }

    @Test
    fun `Validate configuration endpoint can return 400 on invalid GTV`() {
        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Content-Type", "application/octet-stream")
                .body(ByteArray(16))
                .post("/config/$blockchainRID")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", containsString(""))
    }

    @ParameterizedTest
    @MethodSource("invalidConfigExceptions")
    fun `Validate configuration endpoint can return 400 on invalid configuration`(exception: Exception, expectedErrorMessage: String) {
        whenever(model.validateBlockchainConfiguration(any())).thenThrow(exception)

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Content-Type", "text/xml")
                .body(buildXmlBody(config))
                .header(X_POSTCHAIN_SIGNATURE_HEADER, buildValidHeaderAuth(config))
                .post("/config/$blockchainRID")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", equalTo(expectedErrorMessage))
    }

    @Test
    fun `Validate configuration endpoint can return 404 on unknown blockchain RID`() {
        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Content-Type", "text/xml")
                .post("/config/78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a4")
                .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error", startsWith("Can't find blockchain with blockchainRID"))
    }

    @Test
    fun `Validate configuration endpoint don't need signature in manual mode`() {

        setManualModel()

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Content-Type", "application/octet-stream")
                .body(buildByteBody(config))
                .post("/config/$blockchainRID")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo("{}"))
    }

    @Test
    fun `Validate configuration endpoint with valid signature in managed mode`() {

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Content-Type", "application/octet-stream")
                .body(buildByteBody(config))
                .header(X_POSTCHAIN_SIGNATURE_HEADER, buildValidHeaderAuth(config))
                .post("/config/$blockchainRID")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo("{}"))
    }

    @Test
    fun `Validate configuration endpoint managed mode requires signature`() {

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Content-Type", "application/octet-stream")
                .body(buildByteBody(config))
                .post("/config/$blockchainRID")
                .then()
                .statusCode(UNAUTHORIZED.code)
                .contentType(ContentType.JSON)
                .body(equalTo("{\"error\":\"$UNAUTHORIZED_REQUIRE_SIGNATURE_IN_MANAGED_MODE\"}"))
    }

    @Test
    fun `Validate configuration endpoint managed mode requires valid signature`() {

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Content-Type", "application/octet-stream")
                .body(buildByteBody(config))
                .header(X_POSTCHAIN_SIGNATURE_HEADER, buildInvalidSigHashHeaderAuth())
                .post("/config/$blockchainRID")
                .then()
                .statusCode(UNAUTHORIZED.code)
                .contentType(ContentType.JSON)
                .body(equalTo("{\"error\":\"$UNAUTHORIZED_INVALID_SIGNATURE\"}"))
    }

    @Test
    fun `Validate configuration endpoint managed mode signature not correct provider`() {

        setManagedModel(false)

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Content-Type", "application/octet-stream")
                .body(buildByteBody(config))
                .header(X_POSTCHAIN_SIGNATURE_HEADER, buildValidHeaderAuth(config))
                .post("/config/$blockchainRID")
                .then()
                .statusCode(UNAUTHORIZED.code)
                .contentType(ContentType.JSON)
                .body(equalTo("{\"error\":\"$UNAUTHORIZED_CONF_NOT_SIGNED_BY_PROVIDER\"}"))
    }

    @Test
    fun `Validate configuration endpoint managed mode invalid signature followed by valid signature`() {

        setManagedModel(true)

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Content-Type", "application/octet-stream")
                .body(buildByteBody(config))
                .header(X_POSTCHAIN_SIGNATURE_HEADER, buildInvalidAndValidHeaderAuth(config))
                .post("/config/$blockchainRID")
                .then()
                .statusCode(UNAUTHORIZED.code)
                .contentType(ContentType.JSON)
                .body(equalTo("{\"error\":\"$UNAUTHORIZED_INVALID_SIGNATURE\"}"))
    }

    @Test
    fun `Validate configuration endpoint managed mode no signature parameter provided`() {

        setManagedModel(true)

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Content-Type", "application/octet-stream")
                .body(buildByteBody(config))
                .post("/config/$blockchainRID")
                .then()
                .statusCode(UNAUTHORIZED.code)
                .contentType(ContentType.JSON)
                .body(equalTo("{\"error\":\"$UNAUTHORIZED_REQUIRE_SIGNATURE_IN_MANAGED_MODE\"}"))
    }

    private fun buildValidHeaderAuth(config: Gtv): String {

        val signature = sigMaker0.signDigest(config.merkleHash(GtvMerkleHashCalculator(cryptoSystem)))
        return "${signature.subjectID.toHex()}:${signature.data.toHex()}"
    }

    private fun buildInvalidAndValidHeaderAuth(config: Gtv): String {

        val invalid = sigMaker0.signDigest(gtv(0).merkleHash(GtvMerkleHashCalculator(cryptoSystem)))
        val valid = sigMaker0.signDigest(config.merkleHash(GtvMerkleHashCalculator(cryptoSystem)))
        return "Postchain ${invalid.subjectID.toHex()}:${invalid.data.toHex()},${valid.subjectID.toHex()}:${valid.data.toHex()}"
    }

    private fun buildInvalidSigHashHeaderAuth(): String {

        val signature = sigMaker0.signDigest(gtv(0).merkleHash(GtvMerkleHashCalculator(cryptoSystem)))
        return "Postchain ${signature.subjectID.toHex()}:${signature.data.toHex()}"
    }

    private fun buildByteBody(gtvBody: Gtv): ByteArray =
            GtvEncoder.encodeGtv(gtvBody)

    private fun buildXmlBody(gtvBody: Gtv): String =
            GtvMLEncoder.encodeXMLGtv(gtvBody)

    private fun setManualModel() {
        model = buildModel(mock())
    }

    private fun setManagedModel(isBlockchainProvider: Boolean = true) {
        dataSource = mock {
            on { isBlockchainProvider(any(), any()) } doReturn isBlockchainProvider
        }
        val blockchainConfiguration = mock<ManagedBlockchainConfiguration> {
            on { dataSource } doReturn dataSource
        }
        model = buildModel(blockchainConfiguration)
    }

    private fun buildModel(blockchainConfig: BlockchainConfiguration): PostchainModel {
        val postchainContextMock = mock<PostchainContext> {
            on { cryptoSystem } doReturn cryptoSystem
        }

        return mock {
            on { chainIID } doReturn 1L
            on { blockchainRid } doReturn blockchainRID
            on { live } doReturn true
            on { blockchainConfiguration } doReturn blockchainConfig
            on { postchainContext } doReturn postchainContextMock
        }
    }

    companion object {

        private var bccFile: File = Paths.get(RestApiValidateConfigEndpointTest::class.java.getResource("/net/postchain/config/blockchain_config.xml")!!.toURI()).toFile()

        @JvmStatic
        fun invalidConfigExceptions(): List<Array<Any>> = listOf(
                arrayOf(UserMistake("UserMistake message"), "UserMistake message"),
                arrayOf(RuntimeException("Exception message"), "Invalid configuration: Exception message"),
        )
    }
}

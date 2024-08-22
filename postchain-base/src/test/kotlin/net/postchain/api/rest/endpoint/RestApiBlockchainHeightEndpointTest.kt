package net.postchain.api.rest.endpoint

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import net.postchain.api.rest.BlockHeight
import net.postchain.api.rest.blockHeightBody
import net.postchain.api.rest.controller.ExternalModel
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.common.BlockchainRid
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.core.IsEqual.equalTo
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RestApiBlockchainHeightEndpointTest {

    private val basePath = "/api/v1"
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")

    // Master Rest Api
    private lateinit var restApi: RestApi
    private lateinit var restApiRedirected: RestApi
    private lateinit var modelLocal: Model

    // Container foo
    private lateinit var restApiFoo: RestApi
    private lateinit var modelFoo: ExternalModel
    private lateinit var modelFooSubnode: Model

    // Container bar
    private lateinit var restApiBar: RestApi
    private lateinit var modelBar: ExternalModel
    private lateinit var modelBarSubnode: Model

    @BeforeEach
    fun setup() {
        restApi = RestApi(0, basePath, gracefulShutdown = false, clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
        restApiRedirected = RestApi(0, basePath, gracefulShutdown = false, clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), subnodeHttpRedirect = true)
        restApiFoo = RestApi(0, basePath, gracefulShutdown = false, clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
        restApiBar = RestApi(0, basePath, gracefulShutdown = false, clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))

        modelLocal = mock {
            on { chainIID } doReturn 1L
            on { blockchainRid } doReturn blockchainRID
            on { live } doReturn true
            on { getCurrentBlockHeight() } doReturn BlockHeight(15)
        }

        modelFoo = mock {
            on { path } doReturn "http://localhost:${restApiFoo.actualPort()}$basePath"
            on { chainIID } doReturn 1L
            on { live } doReturn true
            on { invoke(any()) } doReturn Response(OK).with(blockHeightBody of BlockHeight(17))
        }

        modelFooSubnode = mock {
            on { chainIID } doReturn 1L
            on { blockchainRid } doReturn blockchainRID
            on { live } doReturn true
            on { getCurrentBlockHeight() } doReturn BlockHeight(18)
        }

        modelBar = mock {
            on { path } doReturn "http://localhost:${restApiBar.actualPort()}$basePath"
            on { chainIID } doReturn 1L
            on { live } doReturn true
            on { invoke(any()) } doReturn Response(OK).with(blockHeightBody of BlockHeight(27))
        }

        modelBarSubnode = mock {
            on { chainIID } doReturn 1L
            on { blockchainRid } doReturn blockchainRID
            on { live } doReturn true
            on { getCurrentBlockHeight() } doReturn BlockHeight(28)
        }
    }

    @AfterEach
    fun tearDown() {
        restApi.close()
    }

    @Test
    fun `blockchain height toward local model`() {
        restApi.attachModel(blockchainRID, modelLocal)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/blockchain/$blockchainRID/height")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("blockHeight", equalTo(15))
    }

    @Test
    fun `external model without redirect with provided container param`() {
        restApi.attachModel(blockchainRID, modelFoo, "foo")
        restApi.attachModel(blockchainRID, modelBar, "bar")

        // foo
        given().basePath(basePath).port(restApi.actualPort())
                .get("/blockchain/$blockchainRID/height?container=foo")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("blockHeight", equalTo(17))

        // bar
        given().basePath(basePath).port(restApi.actualPort())
                .get("/blockchain/$blockchainRID/height?container=bar")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("blockHeight", equalTo(27))

        // unknown
        given().basePath(basePath).port(restApi.actualPort())
                .get("/blockchain/$blockchainRID/height?container=unknown")
                .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error", containsString("Can't find blockchain with blockchainRID: $blockchainRID in the container 'unknown'"))
    }

    @Test
    fun `external model with redirect with provided container param`() {
        restApiRedirected.attachModel(blockchainRID, modelFoo, "foo")
        restApiRedirected.attachModel(blockchainRID, modelBar, "bar")
        restApiFoo.attachModel(blockchainRID, modelFooSubnode)
        restApiBar.attachModel(blockchainRID, modelBarSubnode)

        // foo
        given().basePath(basePath).port(restApiRedirected.actualPort())
                .get("/blockchain/$blockchainRID/height?container=foo")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("blockHeight", equalTo(18))

        // bar
        given().basePath(basePath).port(restApiRedirected.actualPort())
                .get("/blockchain/$blockchainRID/height?container=bar")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("blockHeight", equalTo(28))

        // unknown
        given().basePath(basePath).port(restApiRedirected.actualPort())
                .get("/blockchain/$blockchainRID/height?container=unknown")
                .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error", containsString("Can't find blockchain with blockchainRID: $blockchainRID in the container 'unknown'"))
    }

    @Test
    fun `external model without redirect without provided container param`() {
        restApi.attachModel(blockchainRID, modelFoo, "foo")
        restApi.attachModel(blockchainRID, modelBar, "bar")

        // foo
        given().basePath(basePath).port(restApi.actualPort())
                .get("/blockchain/$blockchainRID/height")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("blockHeight", equalTo(17))
        // foo / empty param
        given().basePath(basePath).port(restApi.actualPort())
                .get("/blockchain/$blockchainRID/height?container=")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("blockHeight", equalTo(17))

        // bar
        given().basePath(basePath).port(restApi.actualPort())
                .get("/blockchain/$blockchainRID/height")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("blockHeight", equalTo(17)) // same as `foo`
    }

    @Test
    fun `external model with redirect without provided container param`() {
        restApiRedirected.attachModel(blockchainRID, modelFoo, "foo")
        restApiRedirected.attachModel(blockchainRID, modelBar, "bar")
        restApiFoo.attachModel(blockchainRID, modelFooSubnode)
        restApiBar.attachModel(blockchainRID, modelBarSubnode)

        // foo
        given().basePath(basePath).port(restApiRedirected.actualPort())
                .get("/blockchain/$blockchainRID/height")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("blockHeight", equalTo(18))
        // foo / empty param
        given().basePath(basePath).port(restApiRedirected.actualPort())
                .get("/blockchain/$blockchainRID/height?container=")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("blockHeight", equalTo(18))

        // bar
        given().basePath(basePath).port(restApiRedirected.actualPort())
                .get("/blockchain/$blockchainRID/height")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("blockHeight", equalTo(18)) // same as `foo`
    }
}

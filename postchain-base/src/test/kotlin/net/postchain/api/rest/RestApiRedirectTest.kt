package net.postchain.api.rest

import io.restassured.RestAssured.given
import io.restassured.response.Response
import net.postchain.api.rest.controller.HttpHelper.Companion.ACCESS_CONTROL_ALLOW_HEADERS
import net.postchain.api.rest.controller.HttpHelper.Companion.ACCESS_CONTROL_ALLOW_METHODS
import net.postchain.api.rest.controller.HttpHelper.Companion.ACCESS_CONTROL_ALLOW_ORIGIN
import net.postchain.api.rest.controller.HttpHelper.Companion.ACCESS_CONTROL_REQUEST_HEADERS
import net.postchain.api.rest.controller.HttpHelper.Companion.ACCESS_CONTROL_REQUEST_METHOD
import net.postchain.api.rest.controller.HttpHelper.Companion.PARAM_BLOCKCHAIN_RID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import spark.Request
import spark.Route
import spark.Service
import spark.Service.ignite
import kotlin.test.assertEquals

/** POS-129: Testing of Spark's redirect feature */

class RestApiRedirectTest {

    private val brid = "78967BAA4768CBCEF11C508326FFB13A956689FCB6DC3BA17F4B895CBB1577A3"
    private val brid2 = "78967BAA4768CBCEF11C508326FFB13A956689FCB6DC3BA17F4B895CBB1577A4"
    private lateinit var http: Service

    @BeforeEach
    fun setUp() {
        http = ignite()!!
        http.port(9999)
        http.before { req, res ->
            res.header(ACCESS_CONTROL_ALLOW_ORIGIN, "*")
            res.header(ACCESS_CONTROL_REQUEST_METHOD, "POST, GET, OPTIONS")
            res.type("application/json")
            // This is to provide compatibility with old postchain-client code
            req.pathInfo()
                    .takeIf { it.endsWith("/") }
                    ?.also { res.redirect(it.dropLast(1)) }
        }

        http.path("/") {
            http.options("/*") { request, response ->
                request.headers(ACCESS_CONTROL_REQUEST_HEADERS)?.let {
                    response.header(ACCESS_CONTROL_ALLOW_HEADERS, it)
                }
                request.headers(ACCESS_CONTROL_REQUEST_METHOD)?.let {
                    response.header(ACCESS_CONTROL_ALLOW_METHODS, it)
                }

                "OK"
            }

            http.get("/blocks/$PARAM_BLOCKCHAIN_RID", "application/json") { _, _ ->
                "Hello Kitty"
            }
        }
    }

    @AfterEach
    fun tearDown() {
        http.stop()
    }

    fun test() {
        val res: Response = given().basePath("/").port(9999)
                .get("/blocks/$brid")
                .then().extract().response()
        assertEquals("Hello Kitty", res.body.prettyPrint())

        // Setting up redirect to example.com
        val redirect = Redirect("http://example.com/")
        http.path("/") {
//            http.redirect.get("/blocks/$brid2", "http://example.com/")
            http.get("/blocks/$brid2", redirect)
        }

        // Asserting redirect is set up
        val res2: Response = given().basePath("/").port(9999)
                .get("/blocks/$brid2")
                .then().extract().response()
        assertEquals("Example Domain", res2.body.htmlPath().getString("html.head.title"))

        // Removing redirect
        redirect.disable()

        // Asserting redirect is cleared
        val res3: Response = given().basePath("/").port(9999)
                .get("/blocks/$brid2")
                .then().extract().response()
        assertEquals("<html><body><h2>404 Not found</h2></body></html>", res3.body.prettyPrint())

        // Enabling redirect again
        redirect.enable()
        // Asserting redirect is set up again
        val res4: Response = given().basePath("/").port(9999)
                .get("/blocks/$brid2")
                .then().extract().response()
        assertEquals("Example Domain", res4.body.htmlPath().getString("html.head.title"))

    }

    class Redirect(val toPath: String, var enabled: Boolean = true) : Route {
        override fun handle(request: Request?, response: spark.Response?): Any? {
            if (enabled) {
                response?.redirect(toPath)
            } else {
                println("Redirect is disabled")
                response?.status(400)
            }
            return null
        }

        fun enable() {
            enabled = true
        }

        fun disable() {
            enabled = false
        }
    }

}
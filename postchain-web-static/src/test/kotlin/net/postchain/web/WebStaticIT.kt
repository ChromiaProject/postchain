// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.web

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.restassured.RestAssured.given
import net.postchain.common.hexStringToByteArray
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.utils.configuration.SystemSetup
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import org.hamcrest.core.IsEqual
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WebStaticIT : IntegrationTestSetup() {

    private val chainIid = 1

    private fun doSystemSetup(): SystemSetup {
        configOverrides.setProperty("testpeerinfos", createPeerInfos(1))
        val bcConfFileMap = mapOf(chainIid to "/net/postchain/web/blockchain_config.xml")
        val sysSetup = SystemSetupFactory.buildSystemSetup(bcConfFileMap)
        assertEquals(1, sysSetup.nodeMap.size, "We didn't get the nodes we expected, check BC config file")
        sysSetup.needRestApi = true // NOTE!! This is important in this test!!

        createNodesFromSystemSetup(sysSetup)
        return sysSetup
    }

    private val mainIndexHtml = """          
          <!DOCTYPE html>
          <html lang="en">
            <head>
              <link rel="stylesheet" href="css/style.css">
              <title>My Rell Dapp</title>
            </head>
            <body>
              <h1>My Rell Dapp</h1>
              <p><image src="img/image.png"></p>
            </body>
          </html>
          
          """.trimIndent()

    private val otherIndexHtml = """          
          <!DOCTYPE html>
          <html lang="en">
            <head>
              <link rel="stylesheet" href="css/style.css">
              <title>Something else</title>
            </head>
            <body>
              <h1>Whatever</h1>
            </body>
          </html>
          
          """.trimIndent()

    private val mainCss = """          
          h1 {
            text-color: green;
          }
          
          """.trimIndent()

    @Test
    fun testWebStatic() {
        val sysSetup = doSystemSetup()
        val blockchainRIDBytes = sysSetup.blockchainMap[chainIid]!!.rid
        val blockchainRID = blockchainRIDBytes.toHex()

        assertThat(given().port(nodes[0].getRestApiHttpPort())
                .get("/web_query/$blockchainRID/web_static/index.html")
                .then()
                .statusCode(200)
                .contentType("text/html")
                .header("Cache-Control", IsEqual.equalTo("public, max-age=3600"))
                .extract().asString())
                .isEqualTo(mainIndexHtml)

        assertThat(given().port(nodes[0].getRestApiHttpPort())
                .get("/web_query/$blockchainRID/web_static/")
                .then()
                .statusCode(200)
                .contentType("text/html")
                .header("Cache-Control", IsEqual.equalTo("public, max-age=3600"))
                .extract().asString())
                .isEqualTo(mainIndexHtml)

        assertThat(given().port(nodes[0].getRestApiHttpPort())
                .get("/web_query/$blockchainRID/web_static")
                .then()
                .statusCode(200)
                .contentType("text/html")
                .header("Cache-Control", IsEqual.equalTo("public, max-age=3600"))
                .extract().asString())
                .isEqualTo(mainIndexHtml)

        assertThat(given().port(nodes[0].getRestApiHttpPort())
                .get("/web_query/$blockchainRID/web_static/other/index.html")
                .then()
                .statusCode(200)
                .contentType("text/html")
                .header("Cache-Control", IsEqual.equalTo("public, max-age=3600"))
                .extract().asString())
                .isEqualTo(otherIndexHtml)

        assertThat(given().port(nodes[0].getRestApiHttpPort())
                .get("/web_query/$blockchainRID/web_static/other/")
                .then()
                .statusCode(200)
                .contentType("text/html")
                .header("Cache-Control", IsEqual.equalTo("public, max-age=3600"))
                .extract().asString())
                .isEqualTo(otherIndexHtml)

        assertThat(given().port(nodes[0].getRestApiHttpPort())
                .get("/web_query/$blockchainRID/web_static/other")
                .then()
                .statusCode(200)
                .contentType("text/html")
                .header("Cache-Control", IsEqual.equalTo("public, max-age=3600"))
                .extract().asString())
                .isEqualTo(otherIndexHtml)

        assertThat(given().port(nodes[0].getRestApiHttpPort())
                .get("/web_query/$blockchainRID/web_static/css/style.css")
                .then()
                .statusCode(200)
                .contentType("text/css")
                .header("Cache-Control", IsEqual.equalTo("public, max-age=3600"))
                .extract().asString())
                .isEqualTo(mainCss)

        assertThat(given().port(nodes[0].getRestApiHttpPort())
                .get("/web_query/$blockchainRID/web_static/img/image.png")
                .then()
                .statusCode(200)
                .contentType("image/png")
                .header("Cache-Control", IsEqual.equalTo("public, max-age=3600"))
                .extract().asByteArray())
                .isEqualTo("1234ABCD".hexStringToByteArray())

        given().port(nodes[0].getRestApiHttpPort())
                .get("/web_query/$blockchainRID/web_static/bogus")
                .then()
                .statusCode(400)
    }
}

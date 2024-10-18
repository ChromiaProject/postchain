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

    private fun doSystemSetup(nodeCount: Int, bcConfFileName: String): SystemSetup {
        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodeCount))
        val bcConfFileMap = mapOf(chainIid to bcConfFileName)
        val sysSetup = SystemSetupFactory.buildSystemSetup(bcConfFileMap)
        assertEquals(nodeCount, sysSetup.nodeMap.size, "We didn't get the nodes we expected, check BC config file")
        sysSetup.needRestApi = true // NOTE!! This is important in this test!!

        createNodesFromSystemSetup(sysSetup)
        return sysSetup
    }

    @Test
    fun testWebStatic() {
        val sysSetup = doSystemSetup(1, "/net/postchain/web/blockchain_config.xml")
        val blockchainRIDBytes = sysSetup.blockchainMap[chainIid]!!.rid
        val blockchainRID = blockchainRIDBytes.toHex()

        val html = given().port(nodes[0].getRestApiHttpPort())
                .get("/web_query/$blockchainRID/web_static/index.html")
                .then()
                .statusCode(200)
                .contentType("text/html")
                .header("Cache-Control", IsEqual.equalTo("public, max-age=3600"))
                .extract().asString()
        assertThat(html).isEqualTo("""          
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
              
              """.trimIndent())

        val css = given().port(nodes[0].getRestApiHttpPort())
                .get("/web_query/$blockchainRID/web_static/css/style.css")
                .then()
                .statusCode(200)
                .contentType("text/css")
                .header("Cache-Control", IsEqual.equalTo("public, max-age=3600"))
                .extract().asString()
        assertThat(css).isEqualTo("""          
              h1 {
                text-color: green;
              }
              
              """.trimIndent())

        val image = given().port(nodes[0].getRestApiHttpPort())
                .get("/web_query/$blockchainRID/web_static/img/image.png")
                .then()
                .statusCode(200)
                .contentType("image/png")
                .header("Cache-Control", IsEqual.equalTo("public, max-age=3600"))
                .extract().asByteArray()
        assertThat(image).isEqualTo("1234ABCD".hexStringToByteArray())
    }
}

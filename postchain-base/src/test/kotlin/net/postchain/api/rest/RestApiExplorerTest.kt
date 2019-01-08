package net.postchain.api.rest

import io.restassured.RestAssured.given
import net.postchain.api.rest.controller.PostchainModel
import net.postchain.api.rest.controller.Query
import net.postchain.api.rest.controller.QueryResult
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.model.ApiStatus
import net.postchain.api.rest.model.ApiTx
import net.postchain.api.rest.model.TxRID
import net.postchain.base.data.BaseExplorerQuery
import net.postchain.base.data.BaseTransactionQueue
import net.postchain.base.data.TypeOfSystemQuery
import net.postchain.common.hexStringToByteArray
import net.postchain.core.TransactionQueue
import net.postchain.core.TransactionStatus
import org.easymock.EasyMock.*
import org.junit.After
import org.junit.Before
import org.junit.Test


class RestApiExplorerTest {

    private val basePath = "api/v1"
    private lateinit var restApi: RestApi
    private lateinit var model: PostchainModel
    private val port = 0
    private val blockchainRID = "78967BAA4768CBCEF11C508326FFB13A956689FCB6DC3BA17F4B895CBB1577A3"
    private val txRID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    @Before
    fun setup() {
        model = createMock(PostchainModel::class.java)
        restApi = RestApi(port, basePath)
    }

    @After
    fun tearDown() {
        restApi.stop()
    }

    @Test
    fun test_query_system() {
        restApi.attachModel(blockchainRID, model)

//        val baseExplorerQuery = BaseExplorerQuery(TypeOfSystemQuery.block, null, null, null)
//        expect(model.querySystem(blockchainRID, baseExplorerQuery)).andReturn(QueryResult(""))
//
//        replay(model)

        given().basePath(basePath).port(restApi.actualPort())
                .body("""{
	"component": "block"
}""")
                .post("/query/system/${blockchainRID}")
                .then()
                .statusCode(200)

//        verify(model)
    }
}
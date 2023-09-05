// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.endpoint

import io.restassured.RestAssured.given
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.model.TxRid
import net.postchain.base.BaseBlockWitness
import net.postchain.base.ConfirmationProof
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.merkle.path.ArrayGtvPathElement
import net.postchain.gtv.merkle.proof.GtvMerkleProofTree
import net.postchain.gtv.merkle.proof.ProofNodeGtvArrayHead
import net.postchain.gtv.merkle.proof.ProofValueGtvLeaf
import org.hamcrest.core.IsEqual.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * `GetConfirmation` and `GetTx` endpoints have common part,
 * so see [RestApiGetTxEndpointTest] for additional tests
 */
class RestApiGetConfirmationProofEndpointTest {

    private val basePath = "/api/v1"
    private lateinit var restApi: RestApi
    private lateinit var model: Model
    private lateinit var proof: GtvMerkleProofTree
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")
    private val txHashHex = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    @BeforeEach
    fun setup() {
        model = mock {
            on { chainIID } doReturn 1L
            on { blockchainRid } doReturn blockchainRID
            on { live } doReturn true
        }

        restApi = RestApi(0, basePath, gracefulShutdown = false)

        proof = buildDummyProof()
    }

    private fun buildDummyProof(): GtvMerkleProofTree {
        val gtv1 = gtv(1)
        val gtv2 = gtv(2)
        val gtvList = listOf(gtv1, gtv2)
        val rootProofElem = ProofNodeGtvArrayHead(
                gtvList.size,
                ProofValueGtvLeaf(gtv1, 0, ArrayGtvPathElement(null, 0)),
                ProofValueGtvLeaf(gtv2, 0, ArrayGtvPathElement(null, 1))
        )
        return GtvMerkleProofTree(rootProofElem)
    }

    @AfterEach
    fun tearDown() {
        restApi.close()
    }

    /**
     * NOTE: Our "model" is just a mock, so this test won't execute any logic outside of the REST API itself.
     * To verify if the proof really looks as intended, see the test [BaseBlockHeaderMerkleProofTest].
     */
    @Test
    fun `can get confirmation proof`() {
        getConfirmationProofOk(true)
    }

    @Test
    fun `can get confirmation proof even if chain is not live`() {
        getConfirmationProofOk(false)
    }

    private fun getConfirmationProofOk(live: Boolean) {
        val expectedObject = ConfirmationProof(
                txHashHex.hexStringToByteArray(),
                byteArrayOf(0x0a, 0x0b, 0x0c),
                BaseBlockWitness(
                        byteArrayOf(0x0b),
                        arrayOf()),
                proof,
                1L // Position of TX in the block
        )
        val expectedDict = GtvObjectMapper.toGtvDictionary(expectedObject)

        whenever(model.getConfirmationProof(TxRid(txHashHex.hexStringToByteArray())))
                .doReturn(expectedObject)
        whenever(model.live).thenReturn(live)

        restApi.attachModel(blockchainRID, model)


        given().basePath(basePath).port(restApi.actualPort())
                .get("/tx/$blockchainRID/$txHashHex/confirmationProof")
                .then()
                .statusCode(200)
                .body("proof", equalTo(GtvEncoder.encodeGtv(expectedDict).toHex()))
    }
}

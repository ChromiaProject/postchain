package net.postchain.api.rest.controller

import net.postchain.common.BlockchainRid
import net.postchain.common.rest.AnchoringChainCheck
import net.postchain.common.rest.HighestBlockHeightAnchoringCheck
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.EagerDiagnosticValue
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class PostchainModelTest {

    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")

    @Test
    fun getVerifiedAnchoredBlockHeightsBody() {
        val nodeDiagnosticContextMock = mock<NodeDiagnosticContext> {
            on { get(DiagnosticProperty.BLOCKCHAIN_HIGHEST_BLOCK_HEIGHT_CLUSTER_ANCHORING_CHECK) } doReturn EagerDiagnosticValue(mutableMapOf(blockchainRID to AnchoringChainCheck(1000L, true)))
            on { get(DiagnosticProperty.BLOCKCHAIN_HIGHEST_BLOCK_HEIGHT_SYSTEM_ANCHORING_CHECK) } doReturn EagerDiagnosticValue(mutableMapOf(blockchainRID to AnchoringChainCheck(100L, true)))
            on { get(DiagnosticProperty.BLOCKCHAIN_HIGHEST_BLOCK_HEIGHT_EVM_ANCHORING_CHECK) } doReturn EagerDiagnosticValue(mutableMapOf(blockchainRID to AnchoringChainCheck(error =  "Error message")))
        }
        val model = PostchainModel(mock() {
            on { rawConfig } doReturn gtv("")
        }, mock(), mock(), blockchainRID, mock(), mock {
            on { nodeDiagnosticContext } doReturn nodeDiagnosticContextMock
        }, mock(), 100L)

        val result = model.getHighestBlockHeightAnchoringCheckBody(blockchainRID)

        val expected = HighestBlockHeightAnchoringCheck(
                cac = AnchoringChainCheck(1000, true, null),
                sac = AnchoringChainCheck(100, true, null),
                evm = AnchoringChainCheck(error = "Error message")
        )
        assertEquals(expected, result)
    }
}


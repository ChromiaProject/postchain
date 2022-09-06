package net.postchain.d1.icmf.integration

import net.postchain.d1.icmf.IcmfTestTransaction
import net.postchain.devtools.utils.GtxTxIntegrationTestSetup
import net.postchain.devtools.utils.configuration.SystemSetup
import org.junit.jupiter.api.Test

class ImcfIntegrationTest : GtxTxIntegrationTestSetup() {

    @Test
    fun icmfDummy() {
        val mapBcFiles: Map<Int, String> = mapOf(
            1 to "/net/postchain/icmf/integration/blockchain_config_1.xml",
        )

        val sysSetup = SystemSetup.buildComplexSetup(mapBcFiles)

        runXNodes(sysSetup)

        buildBlock(1, 0, IcmfTestTransaction(0))
    }
}
package net.postchain.devtools.utils.configuration

import net.postchain.base.config.BlockchainConfigKeys
import net.postchain.common.toHex
import net.postchain.devtools.KeyPairHelper
import net.postchain.devtools.utils.configuration.pre.BlockchainPreSetup
import net.postchain.gtv.GtvDictionary
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BlockchainPreSetupTest {


    @Test
    fun happyTest() {

        val orgSigners = mutableSetOf<NodeSeqNumber>()
        for (i in 1..5) {
            orgSigners.add(NodeSeqNumber(i))
        }
        val bps =  BlockchainPreSetup.simpleBuild(1, orgSigners.toList())

        val bcGtv = bps.toGtvConfig(mapOf())

        // 1. Get the signers
        val signers = mutableSetOf<NodeSeqNumber>()
        val signersArr = BlockchainConfigKeys.Signers from bcGtv as GtvDictionary
        for (pubkey in signersArr!!.asArray()) {
            val byteArray = pubkey.asByteArray()
            val nodeId = KeyPairHelper.pubKeyFromByteArray(byteArray.toHex())!!
            signers.add(NodeSeqNumber(nodeId))
        }

        assertEquals(orgSigners, signers, "The signers found in the GTV config must be the same as we gave.")


        /*
        // 2 Get dependencies
        val chainRidDependencies = mutableSetOf<BlockchainRid>()
        val dep = bcGtv[BaseBlockchainConfigurationData.KEY_DEPENDENCIES]
        if (dep != null) {
            val bcRelatedInfos = BaseDependencyFactory.build(dep!!)
            for (bcRelatedInfo in bcRelatedInfos) {
                chainRidDependencies.add(bcRelatedInfo.blockchainRid)
            }
        }
         */
    }
}
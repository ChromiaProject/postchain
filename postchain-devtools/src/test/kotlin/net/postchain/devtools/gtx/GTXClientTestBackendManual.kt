// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.devtools.gtx

import net.postchain.configurations.GTXTestModule
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.devtools.EbftIntegrationTest
import org.junit.Test

class GTXClientTestBackendManual: EbftIntegrationTest() {

    @Test
    fun testBuildBlock() {
        configOverrides.setProperty("api.port", 7741)
        configOverrides.setProperty("blockchain.1.configurationfactory", GTXBlockchainConfigurationFactory::class.qualifiedName)
        configOverrides.setProperty("blockchain.1.gtx.modules", GTXTestModule::class.qualifiedName)
        createEbftNodes(1)
        Thread.sleep(600000)
    }
}
// Copyright (c) 2023 ChromaWay AB. See README for license information.

package net.postchain.gtx

import net.postchain.base.BaseBlockchainContext
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.configuration.BlockchainConfigurationOptions
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.common.exception.UserMistake
import net.postchain.core.ExecutionContext
import net.postchain.core.NODE_ID_AUTO
import net.postchain.core.NodeRid
import net.postchain.crypto.devtools.MockCryptoSystem
import net.postchain.crypto.sha256Digest
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.mapper.toObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.Connection

class GTXBlockchainConfigurationTest {

    private val cryptoSystem = MockCryptoSystem()
    private val keyPair = cryptoSystem.generateKeyPair()

    @Test
    fun `valid configuration`() {
        testConfig("valid_configuration.xml")
    }

    @Test
    fun `unknown blockstrategy`() {
        assertThrows<UserMistake> {
            testConfig("unknown_blockstrategy.xml")
        }
    }

    private fun testConfig(fileName: String) {
        val configGtv = GtvMLParser.parseGtvML(javaClass.getResource(fileName)!!.readText())
        val blockchainRid = GtvToBlockchainRidFactory.calculateBlockchainRid(configGtv, ::sha256Digest)
        val configData = configGtv.toObject<BlockchainConfigurationData>()
        val gtxModule = GTXBlockchainConfigurationFactory.makeGtxModule(blockchainRid, configData)
        gtxModule.initializeDB(object : ExecutionContext {
            override val chainID: Long = 1L
            override val conn: Connection
                get() = throw NotImplementedError()
        })

        GTXBlockchainConfiguration(
                configData,
                cryptoSystem,
                BaseBlockchainContext(1, blockchainRid, NODE_ID_AUTO, NodeRid.fromHex("").data),
                cryptoSystem.buildSigMaker(keyPair),
                gtxModule,
                BlockchainConfigurationOptions.DEFAULT
        )
    }
}

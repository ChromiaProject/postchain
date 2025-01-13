// Copyright (c) 2023 ChromaWay AB. See README for license information.

package net.postchain.gtx

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import net.postchain.base.BaseBlockchainContext
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.configuration.BlockchainConfigurationOptions
import net.postchain.base.configuration.BlockchainFeatures
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.core.ExecutionContext
import net.postchain.core.NODE_ID_AUTO
import net.postchain.core.NodeRid
import net.postchain.crypto.devtools.MockCryptoSystem
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.mapper.toObject
import org.junit.jupiter.api.Test
import java.sql.Connection

class GTXBlockchainConfigurationTest {

    private val cryptoSystem = MockCryptoSystem()
    private val keyPair = cryptoSystem.generateKeyPair()

    @Test
    fun `valid configuration with no features`() {
        val configuration = testConfig("valid_configuration.xml")
        assertThat(configuration.configData.features).isEmpty()
    }

    @Test
    fun `valid configuration with supported feature`() {
        val configuration = testConfig("valid_configuration_with_feature.xml")
        assertThat(configuration.configData.features[BlockchainFeatures.merkle_hash_version.name]?.asInteger()).isEqualTo(1L)
    }

    @Test
    fun `unknown blockstrategy`() {
        assertFailure {
            testConfig("unknown_blockstrategy.xml")
        }.isInstanceOf(UserMistake::class).messageContains("Configured class net.postchain.base.DoesNotExist not found")
    }

    @Test
    fun `unrecognized feature`() {
        assertFailure {
            testConfig("unrecognized_feature.xml")
        }.isInstanceOf(UserMistake::class).messageContains("Unrecognized feature: bogus")
    }

    @Test
    fun `unsupported feature version`() {
        assertFailure {
            testConfig("unsupported_feature.xml")
        }.isInstanceOf(ProgrammerMistake::class).messageContains("Unknown merkle hash version 3")
    }

    private fun testConfig(fileName: String): GTXBlockchainConfiguration {
        val configGtv = GtvMLParser.parseGtvML(javaClass.getResource(fileName)!!.readText())
        val configData = configGtv.toObject<BlockchainConfigurationData>()
        val blockchainRid = GtvToBlockchainRidFactory.calculateBlockchainRid(configData)
        val gtxModule = GTXBlockchainConfigurationFactory.makeGtxModule(blockchainRid, configData)
        gtxModule.initializeDB(object : ExecutionContext {
            override val chainID: Long = 1L
            override val conn: Connection
                get() = throw NotImplementedError()
        })

        return GTXBlockchainConfiguration(
                configData,
                cryptoSystem,
                BaseBlockchainContext(1, blockchainRid, NODE_ID_AUTO, NodeRid.fromHex("").data),
                cryptoSystem.buildSigMaker(keyPair),
                gtxModule,
                BlockchainConfigurationOptions.DEFAULT
        )
    }
}

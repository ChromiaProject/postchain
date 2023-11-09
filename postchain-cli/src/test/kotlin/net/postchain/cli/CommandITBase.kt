package net.postchain.cli

import assertk.assertThat
import assertk.assertions.isTrue
import assertk.fail
import net.postchain.StorageBuilder
import net.postchain.cli.testutil.TestConsole
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigurationProviderFactory
import net.postchain.core.Storage
import net.postchain.crypto.PubKey
import net.postchain.gtv.GtvFileReader
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File
import java.nio.file.Paths

abstract class CommandITBase {

    @ExtendWith
    val testConsole = TestConsole()

    protected val nodeConfigFile = getFileFromPath("node-config.properties")
    protected val appConfig = AppConfig.fromPropertiesFile(nodeConfigFile)

    protected val chainId = 1L

    protected val blockchainConfig = getFileFromPath("blockchain_config.xml")
    protected val multiSignersBlockchainConfig = getFileFromPath("blockchain_config_4_signers.xml")
    protected val updatedBlockChainConfig = getFileFromPath("blockchain_config_updated.xml")
    protected val invalidBlockChainConfig = getFileFromPath("blockchain_config_invalid.xml")

    protected val brid = "3475C1EEC5836D9B38218F78C30D302DBC7CAAAFFAF0CC83AE054B7A208F71D4"
    protected val blockchainRID = BlockchainRid(brid.hexStringToByteArray())

    protected val signer1PubKey = "0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57"
    protected val signer2PubKey = "035676109C54B9A16D271ABEB4954316A40A32BCCE023AC14C8E26E958AA68FBA9"
    protected val signer3PubKey = "03F811D3E806E6D093A4BCCE49C145BA78F9A4B2FBD167753ECAB2A13530B081F8"
    protected val signer4PubKey = "03EF3F5BE98D499B048BA28B247036B611A1CED7FCF87C17C8B5CA3B3CE1EE23A4"

    protected val host = "127.0.0.1"
    protected val port1 = "9870"
    protected val port2 = "9871"
    protected val port3 = "9872"
    protected val port4 = "9873"

    protected val heightSecondConfig = 10L

    protected lateinit var storage: Storage

    @BeforeEach
    fun beforeEach() {
        storage = StorageBuilder.buildStorage(appConfig, wipeDatabase = true)
    }

    @AfterEach
    fun afterEach() {
        storage.close()
    }

    protected fun addBlockchain(config: File = blockchainConfig) {
        val gtv = GtvFileReader.readFile(config)
        CliExecution.addBlockchain(appConfig, chainId, gtv, AlreadyExistMode.FORCE)
        CliExecution.findBlockchainRid(appConfig, chainId) ?: fail("Failed to add blockchain")
    }

    private fun getFileFromPath(name: String): File {
        return Paths.get(javaClass.getResource("/net/postchain/cli/${name}")!!.toURI()).toFile()
    }

    protected fun addSignersAsPeers() {
        val nodeConfigProvider = NodeConfigurationProviderFactory.createProvider(appConfig, storage)
        val peerInfos = nodeConfigProvider.getConfiguration().peerInfoMap
        for ((_, value) in peerInfos) { // add peer infos for the signers.
            assertThat(CliExecution.peerinfoAdd(appConfig, value.host, value.port, PubKey(value.pubKey.toHex()), AlreadyExistMode.FORCE)).isTrue()
        }
    }
}
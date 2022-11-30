package net.postchain.cli

import assertk.assert
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import net.postchain.StorageBuilder
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.PropertiesFileLoader
import net.postchain.common.toHex
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigurationProviderFactory
import net.postchain.core.Storage
import org.bitcoinj.crypto.MnemonicException.MnemonicLengthException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/* schema name: test0 */
class CliIntegrationIT {

    val nodeConfigPath = fullPath("node-config.properties")
    val appConfig = AppConfig.fromPropertiesFile(nodeConfigPath)
    val chainId = 1L
    val expectedBlockchainRID = "3475C1EEC5836D9B38218F78C30D302DBC7CAAAFFAF0CC83AE054B7A208F71D4"
    val secondBlockChainConfig = fullPath("blockchain_config_4_signers.xml")
    val heightSecondConfig = 10L
    private lateinit var storage: Storage

    private fun fullPath(name: String): File {
        return Paths.get(javaClass.getResource("/net/postchain/cli/${name}")!!.toURI()).toFile()
    }

    @BeforeEach
    fun setup() {
        // this wipes the database.
        storage = StorageBuilder.buildStorage(appConfig, true)
        // add-blockchain goes here
        val blockChainConfig = fullPath("blockchain_config.xml")
        CliExecution.addBlockchain(nodeConfigPath, chainId, blockChainConfig, AlreadyExistMode.FORCE)
    }

    @Test
    fun keygen() {
        val file = kotlin.io.path.createTempFile()
        CommandKeygen().parse(arrayOf("-m", "fever donor long", "-s", file.absolutePathString()))

        val keys = PropertiesFileLoader.load(file.absolutePathString())
        assert(keys.getString("pubkey")).isEqualTo("02B819304E8CFF69D7ACE3C9B2B41346216347972818CCC07F02F05E1FA01D6E63")
        assert(keys.getString("privkey")).isEqualTo("55682A0F")

        val exception = assertThrows<MnemonicLengthException> {
            CommandKeygen().parse(arrayOf("-m", "invalid mnemonic"))
        }
        assert(exception.message).isEqualTo("Word list size must be multiple of three words.")
    }

    @Test
    fun testSetMustSyncUntil() {
        mustSyncUntilIsUpdated(20L)
        //Test that height can be overwritten
        mustSyncUntilIsUpdated(30L)
    }

    private fun mustSyncUntilIsUpdated(height2: Long) {
        CommandMustSyncUntil().parse(
                arrayOf("-nc", nodeConfigPath.absolutePath, "-brid", expectedBlockchainRID, "--height", height2.toString())
        )
        withReadConnection(storage, chainId) {
            assert(DatabaseAccess.of(it).getMustSyncUntil(it)[chainId]).isEqualTo(height2)
        }
    }

    @Test
    fun testAddConfigurationMissingPeerinfo() {
        val exception = assertThrows<CliException> {
            CommandAddConfiguration().parse(
                    arrayOf(
                            "-nc", nodeConfigPath.absolutePath,
                            "-bc", secondBlockChainConfig.absolutePath,
                            "-cid", chainId.toString(),
                            "--height", heightSecondConfig.toString(),
                            "--force"
                    )
            )
        }
        assert(exception.message).contains("MISSING_PEERINFO")
        val configData = CliExecution.getConfiguration(nodeConfigPath, chainId, heightSecondConfig)
        assertNull(configData)
    }

    @Test
    fun testAddConfigurationAllowUnknownSigners() {
        // change configuration with 4 signer at height 10
        CommandAddConfiguration().parse(
                arrayOf(
                        "-nc", nodeConfigPath.absolutePath,
                        "-bc", secondBlockChainConfig.absolutePath,
                        "-cid", chainId.toString(),
                        "--height", heightSecondConfig.toString(),
                        "--allow-unknown-signers",
                        "--force"
                )
        )

        // assert bc added
        CommandCheckBlockchain().parse(arrayOf(
                "-nc", nodeConfigPath.absolutePath,
                "-cid", chainId.toString(),
                "-brid", expectedBlockchainRID
        ))
        // assert config added
        val configData = CliExecution.getConfiguration(nodeConfigPath, chainId, heightSecondConfig)
        assertNotNull(configData)
        val configurations = CliExecution.listConfigurations(nodeConfigPath, chainId)
        assertContains(configurations, heightSecondConfig)
    }

    @Test
    fun testAddConfigurationPeersAdded() {
        // add peerinfos for the new signers.
        val nodeConfigProvider = NodeConfigurationProviderFactory.createProvider(appConfig) { storage }
        val peerinfos = nodeConfigProvider.getConfiguration().peerInfoMap
        for ((_, value) in peerinfos) {
            CommandPeerInfoAdd().parse(arrayOf(
                    "-nc", nodeConfigPath.absolutePath,
                    "--host", value.host,
                    "--port", value.port.toString(),
                    "--pubkey", value.pubKey.toHex(),
                    "--force"
            ))
        }
        // change configuration with 4 signer and height is 10
        val secondBlockChainConfig = fullPath("blockchain_config_4_signers.xml")
        CommandAddConfiguration().parse(
                arrayOf(
                        "-nc", nodeConfigPath.absolutePath,
                        "-bc", secondBlockChainConfig.absolutePath,
                        "-cid", chainId.toString(),
                        "--height", heightSecondConfig.toString(),
                        "--force"
                )
        )
        // assert config added
        val configData = CliExecution.getConfiguration(nodeConfigPath, chainId, heightSecondConfig)
        assertNotNull(configData)
        val configurations = CliExecution.listConfigurations(nodeConfigPath, chainId)
        assertContains(configurations, heightSecondConfig)
    }
}
package net.postchain.cli

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import net.postchain.StorageBuilder
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.toHex
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigurationProviderFactory
import net.postchain.core.Storage
import net.postchain.gtv.GtvFileReader
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

/* schema name: test0 */
class CliIntegrationIT {

    val nodeConfigPath = fullPath("node-config.properties")
    val appConfig = AppConfig.fromPropertiesFile(nodeConfigPath)
    val chainId = 1L
    val expectedBlockchainRID = "3475C1EEC5836D9B38218F78C30D302DBC7CAAAFFAF0CC83AE054B7A208F71D4"
    val secondBlockChainConfig = fullPath("blockchain_config_4_signers.xml")
    val invalidBlockChainConfig = fullPath("blockchain_config_invalid.xml")
    val heightSecondConfig = 10L
    private lateinit var storage: Storage

    private fun fullPath(name: String): File {
        return Paths.get(javaClass.getResource("/net/postchain/cli/${name}")!!.toURI()).toFile()
    }

    @BeforeEach
    fun setup() {
        // this wipes the database.
        storage = StorageBuilder.buildStorage(appConfig, wipeDatabase = true)
        // add-blockchain goes here
        val gtv = GtvFileReader.readFile(fullPath("blockchain_config.xml"))
        CliExecution.addBlockchain(AppConfig.fromPropertiesFile(nodeConfigPath), chainId, gtv, AlreadyExistMode.FORCE)
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
            assertThat(DatabaseAccess.of(it).getMustSyncUntil(it)[chainId]).isEqualTo(height2)
        }
    }

    @Test
    fun testAddInvalidConfiguration() {
        CommandAddConfiguration().parse(
                arrayOf(
                        "-nc", nodeConfigPath.absolutePath,
                        "-bc", invalidBlockChainConfig.absolutePath,
                        "-cid", chainId.toString(),
                        "--height", heightSecondConfig.toString(),
                        "--allow-unknown-signers",
                        "--force"
                )
        )

        val configData = CliExecution.getConfiguration(AppConfig.fromPropertiesFile(nodeConfigPath), chainId, heightSecondConfig)
        assertNull(configData)
    }

    @Test
    fun testAddConfigurationMissingPeerinfo() {
        CommandAddConfiguration().parse(
                arrayOf(
                        "-nc", nodeConfigPath.absolutePath,
                        "-bc", secondBlockChainConfig.absolutePath,
                        "-cid", chainId.toString(),
                        "--height", heightSecondConfig.toString(),
                        "--force"
                )
        )

        val configData = CliExecution.getConfiguration(AppConfig.fromPropertiesFile(nodeConfigPath), chainId, heightSecondConfig)
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

        val appConfig = AppConfig.fromPropertiesFile(nodeConfigPath)

        // assert config added
        val configData = CliExecution.getConfiguration(appConfig, chainId, heightSecondConfig)
        assertNotNull(configData)
        val configurations = CliExecution.listConfigurations(appConfig, chainId)
        assertThat(configurations).contains(heightSecondConfig)
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

        AppConfig.fromPropertiesFile(nodeConfigPath)

        // assert config added
        val configData = CliExecution.getConfiguration(appConfig, chainId, heightSecondConfig)
        assertNotNull(configData)
        val configurations = CliExecution.listConfigurations(appConfig, chainId)
        assertThat(configurations).contains(heightSecondConfig)
    }
}

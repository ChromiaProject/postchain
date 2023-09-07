package net.postchain.devtools

import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.devtools.utils.configuration.NodeConfigurationProviderGenerator
import net.postchain.devtools.utils.configuration.NodeSetup
import net.postchain.devtools.utils.configuration.SystemSetup
import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.PropertiesConfiguration

/**
 * This can build node configurations for both "legacy/manual" and "managed"
 *
 * - "Legacy" just call "createLegacyNodeConfig()"
 * - "Managed" just call "createManagedNodeConfig()"
 *
 * Note: yeah this could have been a part of the [NodeConfigurationProviderGenerator] but I broke it out to make testing
 *       easier (fewer dependencies).
 */
object TestNodeConfigProducer {

    /**
     * Builds a [NodeConfigurationProvider] of type "legacy" and returns it.
     *
     * Here we don't care about the node configuration file (nodeX.properties) at all (most test won't have one).
     *
     * @param testName is the name of the test
     * @param nodeSetup is the node we are working with
     * @param systemSetup is the entire system's config
     * @param startConfig is the config we will use as starting point (but can be overridden by automatic conf), usually nothing

     * @return the [NodeConfigurationProvider] we created
     *
     */
    fun createLegacyNodeConfig(
            testName: String,
            nodeSetup: NodeSetup,
            systemSetup: SystemSetup,
            startConfig: PropertiesConfiguration? = null
    ): Configuration {

        val baseConfig = startConfig ?: PropertiesConfiguration()

        commonSettings(baseConfig, testName, nodeSetup, systemSetup)

        // For legacy, we need to know the peers
        setPeerConfig(nodeSetup, systemSetup, baseConfig)

        return baseConfig
    }

    /**
     * Builds a [NodeConfigurationProvider] of type "managed" and returns it.
     *
     * Here we don't care about the node configuration file (nodeX.properties) at all (most test won't have one).
     *
     * @param testName is the name of the test
     * @param nodeSetup is the node we are working with
     * @param systemSetup is the entire system's config
     * @param startConfig is the config we will use as starting point (but can be overridden by automatic conf), usually nothing

     * @return the [NodeConfigurationProvider] we created
     *
     */
    fun createManagedNodeConfig(
            testName: String,
            nodeSetup: NodeSetup,
            systemSetup: SystemSetup,
            startConfig: PropertiesConfiguration? = null
    ): Configuration {

        val baseConfig = startConfig ?: PropertiesConfiguration()

        commonSettings(baseConfig, testName, nodeSetup, systemSetup)

        // Note: No peers (not needed for managed mode)

        return baseConfig
    }

    private fun commonSettings(
            baseConfig: PropertiesConfiguration,
            testName: String,
            nodeSetup: NodeSetup,
            systemSetup: SystemSetup
    ) {

        // DB
        setDbConfig(testName, nodeSetup, baseConfig)

        // Others
        setSyncTuningParams(systemSetup, baseConfig)

        setConfProvider(systemSetup.nodeConfProvider, baseConfig)
        setConfInfrastructure(systemSetup.confInfrastructure, baseConfig)
        setApiPort(nodeSetup, baseConfig, systemSetup.needRestApi)
        baseConfig.setProperty("api.graceful-shutdown", false)
        setKeys(nodeSetup, baseConfig)
    }

    private fun setSyncTuningParams(systemSetup: SystemSetup, baseConfig: PropertiesConfiguration) {
        baseConfig.setProperty("fastsync.exit_delay", if (systemSetup.nodeMap.size == 1) 0 else 1000)
    }

    private fun setConfProvider(str: String, baseConfig: PropertiesConfiguration) {
        baseConfig.setProperty("configuration.provider.node", str)
    }

    private fun setConfInfrastructure(str: String, baseConfig: PropertiesConfiguration) {
        baseConfig.setProperty("infrastructure", str)
    }

    /**
     * Update the [PropertiesConfiguration] with node info provided by the [NodeSetup]
     */
    private fun setDbConfig(
            testName: String, // For example this could be "multiple_chains_node"
            nodeConf: NodeSetup,
            baseConfig: PropertiesConfiguration
    ) {
        // These are the same for all tests
        baseConfig.setProperty("database.driverclass", "org.postgresql.Driver")
        val dbHost = System.getenv("POSTCHAIN_TEST_DB_HOST") ?: "localhost"
        baseConfig.setProperty("database.url", "jdbc:postgresql://$dbHost:5432/postchain")
        baseConfig.setProperty("database.username", "postchain")
        baseConfig.setProperty("database.password", "postchain")
        // TODO: Maybe a personalized schema name like this is not needed (this is just legacy from the node.properties files)
        val goodTestName = testName.filter { it.isLetterOrDigit() }.lowercase()
        baseConfig.setProperty("database.schema", goodTestName + nodeConf.sequenceNumber.nodeNumber)

        // Legacy way of creating nodes, append nodeIndex to schema name
        val dbSchema = baseConfig.getString("database.schema") + "_" + nodeConf.sequenceNumber.nodeNumber

        // To convert negative indexes of replica nodes to 'replica_' prefixed indexes.
        baseConfig.setProperty("database.schema", dbSchema.replace("-", "replica_"))
    }


    /**
     * Updates the [PropertiesConfiguration] with node info provided by the [NodeSetup]
     *
     * @param nodeSetup contains info about the nodes
     * @param baseConfig is the configuration object we will update
     */
    private fun setPeerConfig(
            nodeSetup: NodeSetup,
            systemSetup: SystemSetup,
            baseConfig: PropertiesConfiguration
    ) {

        for (nodeNr in nodeSetup.calculateAllNodeConnections(systemSetup)) {
            val i = nodeNr.nodeNumber
            baseConfig.setProperty("node.$i.id", "node${i}")
            baseConfig.setProperty("node.$i.host", "127.0.0.1")
            baseConfig.setProperty("node.$i.port", nodeSetup.getPortNumber())
            baseConfig.setProperty("node.$i.pubkey", nodeSetup.pubKeyHex)
        }
    }

    /**
     * Sets the API port, so it won't clash with other nodes.
     */
    private fun setApiPort(
            nodeSetup: NodeSetup,
            baseConfig: PropertiesConfiguration,
            needRestApi: Boolean
    ) {
        if (needRestApi) {
            baseConfig.setProperty("api.port", nodeSetup.getApiPortNumber())
            baseConfig.setProperty("debug.port", nodeSetup.getDebugPortNumber())
        } else {
            baseConfig.setProperty("api.port", -1) // -1 means "don't start"
            baseConfig.setProperty("debug.port", -1) // -1 means "don't start"
        }
    }

    /**
     * Sets the pub and priv keys of the node
     */
    private fun setKeys(
            nodeConf: NodeSetup,
            baseConfig: PropertiesConfiguration
    ) {

        baseConfig.setProperty("messaging.privkey", nodeConf.privKeyHex)
        baseConfig.setProperty("messaging.pubkey", nodeConf.pubKeyHex)
    }
}

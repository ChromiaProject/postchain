package net.postchain.devtools.utils.configuration

import net.postchain.config.app.AppConfig
import net.postchain.devtools.TestNodeConfigProducer
import org.apache.commons.configuration2.CompositeConfiguration
import org.apache.commons.configuration2.MapConfiguration

/**
 * Will extract an [AppConfig] from a [NodeSetup].
 *
 * Background:
 * The entire idea behind the "Setup" test structure is to avoid writing the "nodeX.properties" files, since they
 * are so easy to guess from other data we have. This class does the bulk work when going from [NodeSetup] to the
 * real [AppConfig]
 */
object AppConfigGenerator {

    /**
     * Builds an [AppConfig] from the [NodeSetup]
     *
     * Note that the "manual" and "managed" mode generate similar node configurations, only the peer list is missing
     * from the "managed" configuration.
     *
     * @param testName is the name of the test
     * @param configOverrides is the configurations we always want
     * @param nodeSetup is the node we are working with
     * @param systemSetup is the architecture of the entire system we should test
     * @param setupAction is sometimes used to do an action on the setup
     */
    fun buildFromSetup(
            testName: String,
            configOverrides: MapConfiguration,
            nodeSetup: NodeSetup,
            systemSetup: SystemSetup,
            setupAction: (appConfig: AppConfig) -> Unit = { _ -> }
    ): AppConfig {

        val baseConfig = when (systemSetup.nodeConfProvider) {
            "managed" -> TestNodeConfigProducer.createManagedNodeConfig(testName, nodeSetup, systemSetup, null)
            else -> TestNodeConfigProducer.createLegacyNodeConfig(testName, nodeSetup, systemSetup, null)
        }
        val compositeConfig = CompositeConfiguration().apply {
            addConfiguration(nodeSetup.nodeSpecificConfigs) // The node might have unique config settings, must add these first to "override"
            addConfiguration(configOverrides)
            addConfiguration(baseConfig)
        }
        val appConfig = AppConfig(compositeConfig)

        // Run the action, default won't do anything
        setupAction(appConfig)

        return appConfig
    }
}
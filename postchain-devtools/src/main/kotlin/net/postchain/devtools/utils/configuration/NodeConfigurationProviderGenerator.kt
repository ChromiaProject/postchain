package net.postchain.devtools.utils.configuration

import net.postchain.StorageBuilder
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfig
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.config.node.NodeConfigurationProviderFactory
import net.postchain.core.BaseInfrastructureFactoryProvider
import net.postchain.devtools.TestNodeConfigProducer
import org.apache.commons.configuration2.CompositeConfiguration
import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.MapConfiguration
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler
import java.io.File

/**
 * Will extract a [NodeConfigurationProvider] from a [NodeSetup].
 *
 * Background:
 * The entire idea behind the "Setup" test structure is to avoid writing the "nodeX.properties" files, since they
 * are so easy to guess from other data we have. This class does the bulk work when going from [NodeSetup] to the
 * configuration prodvider needed to get a real [NodeConfig], so the chain becomes:
 *
 * [NodeSetup] -> [NodeConfigurationProvider] -> [NodeConfig]
 */
object NodeConfigurationProviderGenerator {

    /**
     * Builds a [NodeConfigurationProvider] from the [NodeSetup]
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
            setupAction: (appConfig: AppConfig) -> Unit = { _ -> Unit }
    ): NodeConfigurationProvider {

        val baseConfig = when (systemSetup.nodeConfProvider) {
            "managed" -> TestNodeConfigProducer.createManagedNodeConfig(testName, nodeSetup, systemSetup, null)
            else -> TestNodeConfigProducer.createLegacyNodeConfig(testName, nodeSetup, systemSetup, null)
        }
        return buildBase(baseConfig, configOverrides, nodeSetup, setupAction)
    }

    /**
     * Transforms the [PropertiesConfiguration] -> [CompositeConfig] -> [AppConfig] and use the
     * [NodeConfigurationProviderFactory] to build a real [NodeConfigurationProvider].
     *
     * @param baseConfig is the config we have built so far
     * @param configOverrides is the configurations we always want
     * @param nodeSetup
     * @param setupAction is sometimes used to do an action on the setup
     * @return a conf provider where we have overidden the base config with the given overrides.
     */
    private fun buildBase(
        baseConfig: Configuration,
        configOverrides: MapConfiguration,
        nodeSetup: NodeSetup,
        setupAction: (appConfig: AppConfig) -> Unit = { _ -> Unit }
    ): NodeConfigurationProvider {
        val compositeConfig = CompositeConfiguration().apply {
            addConfiguration(nodeSetup.nodeSpecificConfigs) // The node might have unique config settings, must add these first to "override"
            addConfiguration(configOverrides)
            addConfiguration(baseConfig)
        }

        val appConfig = AppConfig(compositeConfig, debug = true)
        val storage = StorageBuilder.buildStorage(appConfig)

        // Run the action, default won't do anything
        setupAction(appConfig)

        return BaseInfrastructureFactoryProvider.createInfrastructureFactory(appConfig).makeNodeConfigurationProvider(appConfig, storage)
    }

    /**
     * Sometimes we want to test that we can read the config file itself.
     * Note: this is not our usual procedure. Most tests will deduce the node configuration from a small set of fields.
     */
    fun readNodeConfFromFile(configFile: String): PropertiesConfiguration {
        // Read first file directly via the builder
        val params = Parameters()
                .fileBased()
//                .setLocationStrategy(ClasspathLocationStrategy())
                .setLocationStrategy(UniversalFileLocationStrategy())
                .setListDelimiterHandler(DefaultListDelimiterHandler(','))
                .setFile(File(configFile))

        return FileBasedConfigurationBuilder(PropertiesConfiguration::class.java)
                .configure(params)
                .configuration
    }
}
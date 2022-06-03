// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain

import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.exporter.HTTPServer
import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigurationProviderFactory
import net.postchain.core.BaseInfrastructureFactoryProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.core.Shutdownable
import net.postchain.core.block.BlockTrace
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.DefaultNodeDiagnosticContext
import net.postchain.debug.DiagnosticProperty
import net.postchain.devtools.NameHelper.peerName
import nl.komponents.kovenant.Kovenant
import java.net.InetSocketAddress

// Metric tags
const val NODE_PUBKEY_TAG = "node.pubkey"
const val CHAIN_IID_TAG = "chainIID"
const val BLOCKCHAIN_RID_TAG = "blockchainRID"
const val RESULT_TAG = "result"

/**
 * Postchain node instantiates infrastructure and blockchain process manager.
 */
open class PostchainNode(appConfig: AppConfig, wipeDb: Boolean = false, debug: Boolean = false) : Shutdownable {

    protected val blockchainInfrastructure: BlockchainInfrastructure
    val processManager: BlockchainProcessManager
    protected val postchainContext: PostchainContext
    private val logPrefix: String

    companion object : KLogging()

    init {
        initMetrics(appConfig)

        Kovenant.context {
            workerContext.dispatcher {
                name = "main"
                concurrentTasks = 5
            }
        }
        val storage = StorageBuilder.buildStorage(appConfig, wipeDb)

        val infrastructureFactory = BaseInfrastructureFactoryProvider.createInfrastructureFactory(appConfig)
        logPrefix = peerName(appConfig.pubKey)

        postchainContext = PostchainContext(
                appConfig,
                NodeConfigurationProviderFactory.createProvider(appConfig) { storage },
                storage,
                infrastructureFactory.makeConnectionManager(appConfig),
                if (debug) DefaultNodeDiagnosticContext() else null
        )
        blockchainInfrastructure = infrastructureFactory.makeBlockchainInfrastructure(postchainContext)
        val blockchainConfigProvider = infrastructureFactory.makeBlockchainConfigurationProvider()
        processManager = infrastructureFactory.makeProcessManager(postchainContext, blockchainInfrastructure, blockchainConfigProvider)

        postchainContext.nodeDiagnosticContext?.apply {
            addProperty(DiagnosticProperty.VERSION, getVersion())
            addProperty(DiagnosticProperty.PUB_KEY, appConfig.pubKey)
            addProperty(DiagnosticProperty.BLOCKCHAIN_INFRASTRUCTURE, blockchainInfrastructure.javaClass.simpleName)
        }
    }

    private fun initMetrics(appConfig: AppConfig) {
        val registry = Metrics.globalRegistry
        registry.config().commonTags(listOf(Tag.of(NODE_PUBKEY_TAG, appConfig.pubKey)))

        ClassLoaderMetrics().bindTo(registry)
        JvmMemoryMetrics().bindTo(registry)
        JvmGcMetrics().bindTo(registry)
        JvmThreadMetrics().bindTo(registry)
        ProcessorMetrics().bindTo(registry)
        UptimeMetrics().bindTo(registry)

        val prometheusPort = appConfig.getInt("metrics.prometheus.port", -1)
        if (prometheusPort > 0) {
            initPrometheus(registry, prometheusPort)
        } else {
            registry.add(SimpleMeterRegistry())
        }
    }

    private fun initPrometheus(registry: CompositeMeterRegistry, port: Int) {
        registry.config().meterFilter(object : MeterFilter {
            override fun configure(id: Meter.Id, config: DistributionStatisticConfig): DistributionStatisticConfig {
                return DistributionStatisticConfig.builder()
                    .percentiles(0.9, 0.95, 0.99)
                    .build()
                    .merge(config)
            }
        })
        val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        registry.add(prometheusRegistry)
        HTTPServer(InetSocketAddress(port), prometheusRegistry.prometheusRegistry, true)
        logger.info("Exposing Prometheus metrics on port $port")
    }

    fun startBlockchain(chainId: Long): BlockchainRid? {
        return processManager.startBlockchain(chainId, buildBbDebug(chainId))
    }

    fun stopBlockchain(chainId: Long) {
        processManager.stopBlockchain(chainId, buildBbDebug(chainId))
    }

    override fun shutdown() {
        // FYI: Order is important
        logger.info("$logPrefix: shutdown() - begin")
        processManager.shutdown()
        logger.debug("$logPrefix: shutdown() - Stopping BlockchainInfrastructure")
        blockchainInfrastructure.shutdown()
        logger.debug("$logPrefix: shutdown() - Stopping PostchainContext")
        postchainContext.shutDown()
        logger.info("$logPrefix: shutdown() - end")
    }

    /**
     * This is for DEBUG operation only
     *
     * @return "true" if we are actually running a test. If we are inside a test we can ofter do more
     * debugging than otherwise
     */
    open fun isThisATest(): Boolean = false

    /**
     * This is for DEBUG operation only
     *
     * We don't care about what the most recent block was, or height at this point.
     * We are just providing the info we have right now
     */
    private fun buildBbDebug(chainId: Long): BlockTrace? {
        return if (logger.isDebugEnabled) {
            val x = processManager.retrieveBlockchain(chainId)
            if (x == null) {
                logger.trace { "WARN why didn't we find the blockchain for chainId: $chainId on node: ${postchainContext.appConfig.pubKey}?" }
                null
            } else {
                val procName = BlockchainProcessName(postchainContext.appConfig.pubKey, x.blockchainEngine.getConfiguration().blockchainRid)
                BlockTrace.buildBeforeBlock(procName)
            }
        } else {
            null
        }
    }

    private fun getVersion(): String {
        return javaClass.getPackage()?.implementationVersion ?: "null"
    }
}

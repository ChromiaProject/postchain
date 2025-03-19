package net.postchain.managed

import net.postchain.PostchainContext
import net.postchain.base.HistoricBlockchainContext
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainState
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import net.postchain.ebft.worker.HistoricBlockchainProcess
import net.postchain.ebft.worker.ReadOnlyBlockchainProcess
import net.postchain.ebft.worker.ValidatorBlockchainProcess
import net.postchain.ebft.worker.WorkerContext
import net.postchain.managed.config.ManagedDataSourceAware
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.cookie.StandardCookieSpec
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.core5.util.TimeValue
import org.apache.hc.core5.util.Timeout
import org.http4k.client.ApacheClient
import org.http4k.core.then
import org.http4k.filter.ClientFilters
import org.http4k.filter.GzipCompressionMode

open class ManagedEBFTSynchronizationInfrastructure(postchainContext: PostchainContext
) : EBFTSynchronizationInfrastructure(postchainContext, DefaultManagedPeersCommConfigFactory()) {
    private val forwardingClient by lazy {
        ClientFilters.GZip(GzipCompressionMode.Streaming()).then(ApacheClient(HttpClients.custom()
                .setRetryStrategy(DefaultHttpRequestRetryStrategy(0, TimeValue.ZERO_MILLISECONDS)) // no retries
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(60))
                                .build())
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setRedirectsEnabled(false)
                        .setCookieSpec(StandardCookieSpec.IGNORE)
                        .setResponseTimeout(Timeout.ofSeconds(60))
                        .build())
                .build()))
    }

    override fun createRunningBlockchainProcess(
            workerContext: WorkerContext,
            historicBlockchainContext: HistoricBlockchainContext?,
            blockchainConfigProvider: BlockchainConfigurationProvider,
            blockchainConfig: BlockchainConfiguration,
            blockchainState: BlockchainState,
            iAmASigner: Boolean
    ): BlockchainProcess = when {
        historicBlockchainContext != null -> HistoricBlockchainProcess(workerContext, historicBlockchainContext)
        iAmASigner -> ValidatorBlockchainProcess(workerContext, getStartWithFastSyncValue(blockchainConfig.chainID), blockchainState)
        else -> {
            val transactionForwarder = if (workerContext.appConfig.forwardingReplica) {
                if (blockchainConfig is ManagedDataSourceAware) {
                    BaseTransactionForwarder(
                            forwardingClient,
                            blockchainConfig.dataSource,
                            blockchainConfig.blockchainRid,
                            workerContext.appConfig.cryptoSystem.random
                    )
                } else {
                    logger.warn("Forwarding replica is enabled, but the BlockchainConfiguration is not ManagedDataSourceAware (it's ${blockchainConfig.javaClass.name})")
                    null
                }
            } else {
                null
            }
            ReadOnlyBlockchainProcess(workerContext, blockchainState, transactionForwarder)
        }
    }
}

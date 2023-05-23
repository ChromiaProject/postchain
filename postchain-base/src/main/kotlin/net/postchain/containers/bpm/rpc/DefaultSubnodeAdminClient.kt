package net.postchain.containers.bpm.rpc

import com.google.protobuf.ByteString
import io.grpc.Grpc
import io.grpc.InsecureChannelCredentials
import io.grpc.ManagedChannel
import io.grpc.Status.ALREADY_EXISTS
import io.grpc.Status.UNAVAILABLE
import io.grpc.Status.fromThrowable
import io.grpc.health.v1.HealthCheckRequest
import io.grpc.health.v1.HealthCheckResponse
import io.grpc.health.v1.HealthGrpc
import io.grpc.protobuf.services.HealthStatusManager
import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.crypto.PrivKey
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.server.grpc.FindBlockchainRequest
import net.postchain.server.grpc.InitNodeRequest
import net.postchain.server.grpc.PeerServiceGrpc
import net.postchain.server.grpc.PostchainServiceGrpc
import net.postchain.server.grpc.StartSubnodeBlockchainRequest
import net.postchain.server.grpc.StopBlockchainRequest
import net.postchain.server.grpc.SubnodeServiceGrpc
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DefaultSubnodeAdminClient(
        private val containerNodeConfig: ContainerNodeConfig,
        private val containerPortMapping: Map<Int, Int>,
        private val nodeDiagnosticContext: NodeDiagnosticContext
) : SubnodeAdminClient {

    companion object : KLogging() {
        private const val RETRY_INTERVAL = 1000
        private const val MAX_RETRIES = 5 * 60 * 1000 / RETRY_INTERVAL // 5 min
        val clientCount = AtomicInteger()
    }

    @Volatile
    private var channel: ManagedChannel? = null

    @Volatile
    private var service: PostchainServiceGrpc.PostchainServiceBlockingStub? = null

    @Volatile
    private var peerService: PeerServiceGrpc.PeerServiceBlockingStub? = null

    @Volatile
    private var healthcheckService: HealthGrpc.HealthBlockingStub? = null

    @Volatile
    private var subnodeService: SubnodeServiceGrpc.SubnodeServiceBlockingStub? = null

    private val executor: ExecutorService = Executors.newSingleThreadExecutor {
        Thread(it, "${clientCount.incrementAndGet()}-DefaultSubnodeAdminClient")
    }

    private var connectJob: Future<*>? = null

    override fun connect() {
        connectJob = executor.submit {
            val target = "${containerNodeConfig.subnodeHost}:${containerPortMapping[containerNodeConfig.subnodeAdminRpcPort]}"
            repeat(MAX_RETRIES) {
                try {
                    logger.debug { "connect() -- Connecting to subnode container on $target ..." }
                    val creds = InsecureChannelCredentials.create()
                    channel = Grpc.newChannelBuilder(target, creds).build()
                    service = PostchainServiceGrpc.newBlockingStub(channel)
                    peerService = PeerServiceGrpc.newBlockingStub(channel)
                    healthcheckService = HealthGrpc.newBlockingStub(channel)
                    subnodeService = SubnodeServiceGrpc.newBlockingStub(channel)
                    logger.info { "connect() -- Subnode container connection established on $target" }
                    return@submit
                } catch (e: Exception) {
                    logger.warn(e) { "connect() -- Can't connect to subnode on $target, attempt $it of $MAX_RETRIES" }
                }

                if (it == MAX_RETRIES - 1) {
                    logger.error { "connect() -- Can't connect to subnode on $target, $MAX_RETRIES failed attempts" }
                } else {
                    Thread.sleep(RETRY_INTERVAL.toLong())
                }
            }
        }
    }

    override fun disconnect() {
        connectJob?.cancel(true)
        channel?.apply {
            shutdownNow()
            awaitTermination(1000, TimeUnit.MILLISECONDS)
        }
    }

    override fun initializePostchainNode(privKey: PrivKey): Boolean {
        return try {
            val request = InitNodeRequest.newBuilder().setPrivkey(ByteString.copyFrom(privKey.data)).build()
            val response = subnodeService?.initNode(request)
                    ?: throw ProgrammerMistake("subnode admin client not connected")
            logger.debug { response.message }
            true
        } catch (e: Exception) {
            if (fromThrowable(e).code == ALREADY_EXISTS.code) {
                logger.info { "initializePostchainNode -- ${e.message}" }
                true
            } else {
                logger.error { "initializePostchainNode -- exception occurred: ${e.message}" }
                false
            }
        }
    }

    override fun isSubnodeHealthy(): Boolean {
        return try {
            logger.debug { "isSubnodeHealthy -- doing health check" }
            val request = HealthCheckRequest.newBuilder().setService(HealthStatusManager.SERVICE_NAME_ALL_SERVICES).build()
            val reply = healthcheckService?.check(request) ?: return false
            reply.status == HealthCheckResponse.ServingStatus.SERVING
        } catch (e: Exception) {
            if (fromThrowable(e).code != UNAVAILABLE.code) {
                logger.warn { "isSubnodeHealthy -- can't do health check: ${e.message}" }
            }
            false
        }
    }

    override fun startBlockchain(chainId: Long, blockchainRid: BlockchainRid): Boolean {
        return try {
            val request = StartSubnodeBlockchainRequest.newBuilder()
                    .setChainId(chainId)
                    .setBrid(ByteString.copyFrom(blockchainRid.data))
                    .build()

            val response = subnodeService?.startSubnodeBlockchain(request)
                    ?: throw ProgrammerMistake("subnode admin client not connected")
            logger.debug { "startBlockchain(${chainId}) -- blockchain started ${response.message}" }
            true
        } catch (e: Exception) {
            nodeDiagnosticContext.blockchainErrorQueue(blockchainRid).add("Can't start blockchain: ${e.message}")
            logger.error { "startBlockchain(${chainId}:${blockchainRid.toShortHex()}) -- can't start blockchain: ${e.message}" }
            false
        }
    }

    override fun stopBlockchain(chainId: Long): Boolean {
        return try {
            val request = StopBlockchainRequest.newBuilder()
                    .setChainId(chainId)
                    .build()

            val response = service?.stopBlockchain(request)
                    ?: throw ProgrammerMistake("subnode admin client not connected")
            logger.debug { "stopBlockchain($chainId) -- blockchain stopped: service's reply: ${response.message}" }
            true
        } catch (e: Exception) {
            logger.error { "stopBlockchain($chainId) -- can't stop blockchain: ${e.message}" }
            false
        }
    }

    override fun isBlockchainRunning(chainId: Long): Boolean {
        return try {
            val request = FindBlockchainRequest.newBuilder()
                    .setChainId(chainId)
                    .build()
            val response = service?.findBlockchain(request)
                    ?: throw ProgrammerMistake("subnode admin client not connected")
            logger.debug { "isBlockchainRunning($chainId) -- ${response.active}" }
            response.active
        } catch (e: Exception) {
            logger.error { "isBlockchainRunning($chainId) -- exception occurred: ${e.message}" }
            false
        }
    }

    override fun getBlockchainLastHeight(chainId: Long): Long {
        return try {
            val request = FindBlockchainRequest.newBuilder()
                    .setChainId(chainId)
                    .build()
            val response = service?.findBlockchain(request)
                    ?: throw ProgrammerMistake("subnode admin client not connected")
            logger.debug { "getBlockchainLastHeight($chainId) -- ${response.height}" }
            response.height
        } catch (e: Exception) {
            logger.error { "getBlockchainLastHeight($chainId) -- exception occurred: ${e.message}" }
            -1L
        }
    }

    override fun shutdown() {
        disconnect()
        executor.shutdown()
    }
}

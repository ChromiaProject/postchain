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
import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.crypto.PrivKey
import net.postchain.debug.ErrorDiagnosticValue
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.server.grpc.AddConfigurationRequest
import net.postchain.server.grpc.ExportBlocksRequest
import net.postchain.server.grpc.FindBlockchainRequest
import net.postchain.server.grpc.ImportBlocksRequest
import net.postchain.server.grpc.InitNodeRequest
import net.postchain.server.grpc.InitializeBlockchainRequest
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
                            .withInterceptors(SubnodeAdminClientTimeoutInterceptor(containerNodeConfig))
                    peerService = PeerServiceGrpc.newBlockingStub(channel)
                            .withInterceptors(SubnodeAdminClientTimeoutInterceptor(containerNodeConfig))
                    healthcheckService = HealthGrpc.newBlockingStub(channel)
                            .withInterceptors(SubnodeAdminClientTimeoutInterceptor(containerNodeConfig))
                    subnodeService = SubnodeServiceGrpc.newBlockingStub(channel)
                            .withInterceptors(SubnodeAdminClientTimeoutInterceptor(containerNodeConfig))
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
            val request = HealthCheckRequest.newBuilder().setService("").build()
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
            nodeDiagnosticContext.blockchainErrorQueue(blockchainRid).add(ErrorDiagnosticValue("Can't start blockchain: ${e.message}", System.currentTimeMillis()))
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

    override fun getBlockchainLastBlockHeight(chainId: Long): Long {
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

    override fun initializeBlockchain(chainId: Long, config: ByteArray) {
        try {
            val request = InitializeBlockchainRequest.newBuilder()
                    .setChainId(chainId)
                    .setGtv(ByteString.copyFrom(config))
                    .setOverride(true)
                    .build()
            val response = service?.initializeBlockchain(request)
                    ?: throw ProgrammerMistake("subnode admin client not connected")
            logger.debug { "initializeBlockchain($chainId) -- ${response.success}" }
        } catch (e: Exception) {
            logger.error { "initializeBlockchain($chainId) -- exception occurred: ${e.message}" }
        }
    }

    override fun addBlockchainConfiguration(chainId: Long, height: Long, config: ByteArray) {
        try {
            val request = AddConfigurationRequest.newBuilder()
                    .setChainId(chainId)
                    .setHeight(height)
                    .setGtv(ByteString.copyFrom(config))
                    .setOverride(true)
                    .setAllowUnknownSigners(true)
                    .build()
            val response = service?.addConfiguration(request)
                    ?: throw ProgrammerMistake("subnode admin client not connected")
            logger.debug { "addBlockchainConfiguration(chainId = $chainId, height = $height) -- ${response.message}" }
        } catch (e: Exception) {
            logger.error { "addBlockchainConfiguration(chainId = $chainId, height = $height) -- exception occurred: ${e.message}" }
        }
    }

    override fun exportBlocks(chainId: Long, fromHeight: Long, blockCountLimit: Int, blocksSizeLimit: Int): List<Gtv> {
        return try {
            val request = ExportBlocksRequest.newBuilder()
                    .setChainId(chainId)
                    .setFromHeight(fromHeight)
                    .setBlockCountLimit(blockCountLimit)
                    .setBlocksSizeLimit(blocksSizeLimit)
                    .build()
            val response = service?.exportBlocks(request)
                    ?: throw ProgrammerMistake("subnode admin client not connected")
            logger.debug { "exportBlocks(chainId = $chainId, fromHeight = $fromHeight, blockCountLimit = $blockCountLimit, blocksSizeLimit = $blocksSizeLimit)" }
            response.blockDataList
                    .map { GtvDecoder.decodeGtv(it.toByteArray()) }
        } catch (e: Exception) {
            logger.error { "exportBlocks(chainId = $chainId, fromHeight = $fromHeight, blockCountLimit = $blockCountLimit, blocksSizeLimit = $blocksSizeLimit) -- exception occurred: ${e.message}" }
            listOf()
        }
    }

    override fun importBlocks(chainId: Long, blockData: List<Gtv>): Long {
        try {
            val request = ImportBlocksRequest.newBuilder()
                    .setChainId(chainId)
                    .addAllBlockData(blockData.map { ByteString.copyFrom(GtvEncoder.encodeGtv(it)) })
                    .build()
            val response = service?.importBlocks(request)
                    ?: throw ProgrammerMistake("subnode admin client not connected")
            logger.debug { "importBlocks(chainId = $chainId) --  fromHeight = ${response.fromHeight}), upToHeight = ${response.upToHeight} -- ${response.message}" }
            return response.upToHeight
        } catch (e: Exception) {
            val errorMessage = "importBlocks(chainId = $chainId) -- exception occurred: ${e.message}"
            logger.error { errorMessage }
            throw ProgrammerMistake(errorMessage)
        }
    }

    override fun shutdown() {
        disconnect()
        executor.shutdown()
    }
}

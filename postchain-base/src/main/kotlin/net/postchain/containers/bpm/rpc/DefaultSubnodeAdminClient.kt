package net.postchain.containers.bpm.rpc

import com.google.protobuf.ByteString
import io.grpc.Grpc
import io.grpc.InsecureChannelCredentials
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.Status.ALREADY_EXISTS
import mu.KLogging
import net.postchain.base.PeerInfo
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.containers.bpm.ContainerPorts
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.server.grpc.AddConfigurationRequest
import net.postchain.server.grpc.AddPeerRequest
import net.postchain.server.grpc.DebugRequest
import net.postchain.server.grpc.DebugServiceGrpc
import net.postchain.server.grpc.FindBlockchainRequest
import net.postchain.server.grpc.InitializeBlockchainRequest
import net.postchain.server.grpc.PeerServiceGrpc
import net.postchain.server.grpc.PostchainServiceGrpc
import net.postchain.server.grpc.StopBlockchainRequest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DefaultSubnodeAdminClient(
        private val containerNodeConfig: ContainerNodeConfig,
        private val containerPorts: ContainerPorts,
) : SubnodeAdminClient {

    companion object : KLogging() {
        private const val RETRY_INTERVAL = 1000
        private const val MAX_RETRIES = 5 * 60 * 1000 / RETRY_INTERVAL // 5 min
        val clientCount = AtomicInteger()
    }

    private lateinit var channel: ManagedChannel
    private lateinit var service: PostchainServiceGrpc.PostchainServiceBlockingStub
    private lateinit var peerService: PeerServiceGrpc.PeerServiceBlockingStub
    private lateinit var healthcheckService: DebugServiceGrpc.DebugServiceBlockingStub

    private val executor: ExecutorService = Executors.newSingleThreadExecutor {
        Thread(it, "${clientCount.incrementAndGet()}-DefaultSubnodeAdminClient")
    }

    override fun connect() {
        executor.submit {
            val target = "${containerNodeConfig.subnodeHost}:${containerPorts.hostAdminRpcPort}"
            repeat(MAX_RETRIES) {
                try {
                    logger.debug { "connect() -- Connecting to subnode container on $target ..." }
                    val creds = InsecureChannelCredentials.create()
                    channel = Grpc.newChannelBuilder(target, creds).build()
                    service = PostchainServiceGrpc.newBlockingStub(channel)
                    peerService = PeerServiceGrpc.newBlockingStub(channel)
                    healthcheckService = DebugServiceGrpc.newBlockingStub(channel)
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

    override fun isSubnodeConnected(): Boolean {
        val initialized = ::service.isInitialized && ::healthcheckService.isInitialized
        if (!initialized) return false

        return try {
            val request = DebugRequest.newBuilder().build()
            val reply = healthcheckService.debugInfo(request) // Asking something
            reply.message != ""
        } catch (e: Exception) {
            logger.error { e.message }
            false
        }
    }

    override fun addConfiguration(chainId: Long, height: Long, override: Boolean, config: ByteArray): Boolean {
        return try {
            val request = AddConfigurationRequest.newBuilder()
                    .setChainId(chainId)
                    .setHeight(height)
                    .setOverride(true)
                    .setGtv(ByteString.copyFrom(config))
                    .build()

            val response = service.addConfiguration(request)
            logger.debug { "addConfiguration(${chainId}) -- ${response.message}" }
            true
        } catch (e: Exception) {
            logger.error { "addConfiguration(${chainId}) -- can't add configuration: ${e.message}" }
            false
        }
    }

    override fun startBlockchain(chainId: Long, blockchainRid: BlockchainRid, config: ByteArray): Boolean {
        return try {
            val request = InitializeBlockchainRequest.newBuilder()
                    .setChainId(chainId)
                    .setOverride(true)
                    .setGtv(ByteString.copyFrom(config))
                    // Original brid is passed to subnode since dataSource.getConfiguration()
                    // returns original config with patched signers and its brid differs from the original one
                    .setBrid(blockchainRid.toHex())
                    .build()

            val response = service.initializeBlockchain(request)
            if (response.brid != null) {
                logger.debug { "startBlockchain(${chainId}) -- blockchain started ${response.brid}" }
                true
            } else {
                logger.error { "startBlockchain(${chainId}) -- can't start blockchain" }
                false
            }
        } catch (e: Exception) {
            logger.error { "startBlockchain(${chainId}) -- can't start blockchain: ${e.message}" }
            false
        }
    }

    override fun stopBlockchain(chainId: Long): Boolean {
        return try {
            val request = StopBlockchainRequest.newBuilder()
                    .setChainId(chainId)
                    .build()

            val response = service.stopBlockchain(request)
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
            val response = service.findBlockchain(request)
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
            val response = service.findBlockchain(request)
            logger.debug { "getBlockchainLastHeight($chainId) -- ${response.height}" }
            response.height
        } catch (e: Exception) {
            logger.error { "getBlockchainLastHeight($chainId) -- exception occurred: ${e.message}" }
            -1L
        }
    }

    override fun addPeerInfo(peerInfo: PeerInfo): Boolean {
        return try {
            val request = AddPeerRequest.newBuilder()
                    .setOverride(true)
                    .setHost(peerInfo.host)
                    .setPort(peerInfo.port)
                    .setPubkey(peerInfo.pubKey.toHex())
                    .build()

            val response = peerService.addPeer(request)
            logger.debug { response.message }
            true
        } catch (e: Exception) {
            if (Status.fromThrowable(e).code == ALREADY_EXISTS.code) {
                logger.info { "addPeerInfo($peerInfo) -- ${e.message}" }
            } else {
                logger.error { "addPeerInfo($peerInfo) -- exception occurred: ${e.message}" }
            }
            false
        }
    }

    override fun shutdown() {
        channel.shutdownNow()
        channel.awaitTermination(1000, TimeUnit.MILLISECONDS)
        executor.shutdown()
    }

}

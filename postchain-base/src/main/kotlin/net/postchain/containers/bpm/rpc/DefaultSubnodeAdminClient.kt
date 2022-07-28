package net.postchain.containers.bpm.rpc

import com.google.protobuf.ByteString
import io.grpc.Grpc
import io.grpc.InsecureChannelCredentials
import io.grpc.ManagedChannel
import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.containers.bpm.ContainerPorts
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.server.service.*
import nl.komponents.kovenant.task
import java.util.concurrent.TimeUnit

class DefaultSubnodeAdminClient(
        private val containerNodeConfig: ContainerNodeConfig,
        private val containerPorts: ContainerPorts,
) : SubnodeAdminClient {

    companion object : KLogging() {
        private const val RETRY_INTERVAL = 1000
        private const val MAX_RETRIES = 5 * 60 * 1000 / RETRY_INTERVAL // 5 min
    }

    private lateinit var channel: ManagedChannel
    private lateinit var service: PostchainServiceGrpc.PostchainServiceBlockingStub
    private lateinit var healthcheckService: DebugServiceGrpc.DebugServiceBlockingStub

    override fun connect() {
        task {
            val target = "${containerNodeConfig.subnodeHost}:${containerPorts.hostAdminRpcPort}"
            repeat(MAX_RETRIES) {
                try {
                    logger.debug { "connect() -- Connecting to subnode container $target ..." }
                    val creds = InsecureChannelCredentials.create()
                    channel = Grpc.newChannelBuilder(target, creds).build()
                    service = PostchainServiceGrpc.newBlockingStub(channel)
                    healthcheckService = DebugServiceGrpc.newBlockingStub(channel)
                    logger.info { "connect() -- Subnode container connection established" }
                    return@task
                } catch (e: Exception) {
                    logger.error(e) { "connect() -- Can't connect to subnode, attempt $it of $MAX_RETRIES" }
                }

                if (it == MAX_RETRIES - 1) {
                    logger.error { "connect() -- Can't connect to subnode, $MAX_RETRIES failed attempts" }
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
            val reply = healthcheckService.debugService(request) // Asking something
            reply.message != ""
        } catch (e: Exception) {
            logger.error { e.message }
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
            logger.error { "stopBlockchain($chainId) -- can't stop blockchain: : ${e.message}" }
            false
        }
    }

    override fun isBlockchainRunning(chainId: Long): Boolean {
        return try {
            val request = FindBlockchainRequest.newBuilder()
                    .setChainId(chainId)
                    .build()
            val response = service.findBlockchain(request)
            logger.debug { "isBlockchainRunning($chainId) -- ${response.running}" }
            response.running
        } catch (e: Exception) {
            logger.error { "isBlockchainRunning($chainId) -- exception occurred: : ${e.message}" }
            false
        }
    }

    override fun shutdown() {
        channel.shutdownNow()
        channel.awaitTermination(1000, TimeUnit.MILLISECONDS)
    }

}
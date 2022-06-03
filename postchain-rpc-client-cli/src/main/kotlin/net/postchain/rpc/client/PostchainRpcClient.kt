package net.postchain.rpc.client

import com.google.protobuf.ByteString
import io.grpc.Channel
import net.postchain.server.rpc.*
import java.io.File
import kotlin.io.path.extension

class PostchainRpcClient(channel: Channel) {

    private val blockingStub = PostchainServiceGrpc.newBlockingStub(channel)
    private val asyncStub = PostchainServiceGrpc.newStub(channel)

    fun startBlockchain(chainId: Long) {
        val request = StartBlockchainRequest.newBuilder()
            .setChainId(chainId)
            .build()

        val reply = blockingStub.startBlockchain(request)
        println(reply.message)
    }

    fun startBlockchain(chainId: Long, conf: File, override: Boolean = false) {
        val requestBuilder = InitializeBlockchainRequest.newBuilder()
            .setChainId(chainId)
            .setOverride(override)

        when (conf.toPath().extension) {
            "gtv" -> requestBuilder.gtv = ByteString.copyFrom(conf.readBytes())
            "xml" -> requestBuilder.xml = conf.readText()
            else -> throw IllegalArgumentException("File must be xml or gtv file")
        }
        val reply = blockingStub.initializeBlockchain(requestBuilder.build())
        println(reply.message)
    }

    fun stopBlockchain(chainId: Long) {
        val request = StopBlockchainRequest.newBuilder()
            .setChainId(chainId)
            .build()

        val reply = blockingStub.stopBlockchain(request)
        println(reply.message)
    }

    fun addConfiguration(chainId: Long, height: Long, conf: File, override: Boolean = false) {
        val requestBuilder = AddConfigurationRequest.newBuilder()
            .setChainId(chainId)
            .setHeight(height)
            .setOverride(override)

        when (conf.toPath().extension) {
            "gtv" -> requestBuilder.gtv = ByteString.copyFrom(conf.readBytes())
            "xml" -> requestBuilder.xml = conf.readText()
            else -> throw IllegalArgumentException("File must be xml or gtv file")
        }
        val reply = blockingStub.addConfiguration(requestBuilder.build())
        println(reply.message)
    }

    fun addPeer(host: String, port: Int, pubkey: String, override: Boolean = false) {
        val request = AddPeerRequest.newBuilder()
            .setHost(host)
            .setPort(port)
            .setPubkey(pubkey)
            .setOverride(override)
            .build()

        val reply = blockingStub.addPeer(request)
        println(reply.message)
    }
}
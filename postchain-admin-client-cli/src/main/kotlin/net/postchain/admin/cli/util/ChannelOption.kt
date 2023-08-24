package net.postchain.admin.cli.util

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.grpc.Channel
import io.grpc.ChannelCredentials
import io.grpc.Grpc
import io.grpc.InsecureChannelCredentials
import io.grpc.TlsChannelCredentials
import io.grpc.health.v1.HealthGrpc
import net.postchain.server.grpc.DebugServiceGrpc
import net.postchain.server.grpc.PeerServiceGrpc
import net.postchain.server.grpc.PostchainServiceGrpc

typealias ChannelFactory = (String, ChannelCredentials) -> Channel

val DEFAULT_CHANNEL_FACTORY: ChannelFactory = { target, cred -> Grpc.newChannelBuilder(target, cred).build() }

fun CliktCommand.channelOption(channelFactory: ChannelFactory) = option("-t", "--target", envvar = "POSTCHAIN_TARGET", help = "Target path for command. On the form host:port")
        .convert {
            val tlsOptions = currentContext.findObject<TlsConfig>()
            val channelCredentials = if (tlsOptions != null && tlsOptions.enabled) {
                buildTlsCredentials(tlsOptions)
            } else {
                InsecureChannelCredentials.create()
            }
            channelFactory(it, channelCredentials)
        }

fun buildTlsCredentials(tlsOptions: TlsConfig): ChannelCredentials {
    return if (tlsOptions.certificateFile != null) {
        TlsChannelCredentials.newBuilder().trustManager(tlsOptions.certificateFile).build()
    } else {
        TlsChannelCredentials.create()
    }
}

fun CliktCommand.blockingPostchainServiceChannelOption(channelFactory: ChannelFactory) = channelOption(channelFactory)
        .convert { PostchainServiceGrpc.newBlockingStub(it) }
        .required()

fun CliktCommand.blockingPeerServiceChannelOption(channelFactory: ChannelFactory) = channelOption(channelFactory)
        .convert { PeerServiceGrpc.newBlockingStub(it) }
        .required()

fun CliktCommand.blockingDebugServiceChannelOption(channelFactory: ChannelFactory) = channelOption(channelFactory)
        .convert { DebugServiceGrpc.newBlockingStub(it) }
        .required()

fun CliktCommand.blockingHealthServiceChannelOption(channelFactory: ChannelFactory) = channelOption(channelFactory)
        .convert { HealthGrpc.newBlockingStub(it) }
        .required()

package net.postchain.admin.cli.util

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.grpc.ChannelCredentials
import io.grpc.Grpc
import io.grpc.InsecureChannelCredentials
import io.grpc.TlsChannelCredentials
import io.grpc.health.v1.HealthGrpc
import net.postchain.server.grpc.DebugServiceGrpc
import net.postchain.server.grpc.PeerServiceGrpc
import net.postchain.server.grpc.PostchainServiceGrpc

fun CliktCommand.channelOption() = option("-t", "--target", envvar = "POSTCHAIN_TARGET", help = "Target path for command. On the form host:port")
    .convert {
        val tlsOptions = currentContext.findObject<TlsConfig>()
        val channelCredentials = if (tlsOptions != null && tlsOptions.enabled) {
            buildTlsCredentials(tlsOptions)
        } else {
            InsecureChannelCredentials.create()
        }
        Grpc.newChannelBuilder(it, channelCredentials).build()
    }

fun buildTlsCredentials(tlsOptions: TlsConfig): ChannelCredentials {
    return if (tlsOptions.certificateFile != null) {
        TlsChannelCredentials.newBuilder().trustManager(tlsOptions.certificateFile).build()
    } else {
        TlsChannelCredentials.create()
    }
}

fun CliktCommand.blockingPostchainServiceChannelOption() = channelOption()
    .convert { PostchainServiceGrpc.newBlockingStub(it) }
        .required()

fun CliktCommand.blockingPeerServiceChannelOption() = channelOption()
        .convert { PeerServiceGrpc.newBlockingStub(it) }
        .required()

fun CliktCommand.blockingDebugServiceChannelOption() = channelOption()
        .convert { DebugServiceGrpc.newBlockingStub(it) }
        .required()

fun CliktCommand.blockingHealthServiceChannelOption() = channelOption()
        .convert { HealthGrpc.newBlockingStub(it) }
        .required()

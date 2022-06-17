package net.postchain.admin.cli.util

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.grpc.ChannelCredentials
import io.grpc.Grpc
import io.grpc.InsecureChannelCredentials
import io.grpc.TlsChannelCredentials
import net.postchain.server.service.DebugServiceGrpc
import net.postchain.server.service.PeerServiceGrpc
import net.postchain.server.service.PostchainServiceGrpc

fun CliktCommand.channelOption() = option("-t", "--target", envvar = "POSTCHAIN_TARGET", help = "Target path for command. On the form host:port")
    .convert {
        val sslOptions = currentContext.findObject<SslConfig>()
        val channelCredentials = if (sslOptions != null && sslOptions.enabled) {
            buildTlsCredentials(sslOptions)
        } else {
            InsecureChannelCredentials.create()
        }
        Grpc.newChannelBuilder(it, channelCredentials).build()
    }

fun buildTlsCredentials(sslOptions: SslConfig): ChannelCredentials {
    return if (sslOptions.certificateFile != null) {
        TlsChannelCredentials.newBuilder().trustManager(sslOptions.certificateFile).build()
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

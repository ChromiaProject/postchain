package net.postchain.admin.cli.util

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.grpc.ManagedChannelBuilder
import net.postchain.server.service.DebugServiceGrpc
import net.postchain.server.service.PeerServiceGrpc
import net.postchain.server.service.PostchainServiceGrpc

fun CliktCommand.channelOption() = option("-t", "--target", envvar = "POSTCHAIN_TARGET", help = "Target path for command. On the form host:port")
    .convert { ManagedChannelBuilder.forTarget(it).usePlaintext().build() }

fun CliktCommand.blockingPostchainServiceChannelOption() = channelOption()
    .convert { PostchainServiceGrpc.newBlockingStub(it) }
    .required()

fun CliktCommand.blockingPeerServiceChannelOption() = channelOption()
    .convert { PeerServiceGrpc.newBlockingStub(it) }
    .required()

fun CliktCommand.blockingDebugServiceChannelOption() = channelOption()
    .convert { DebugServiceGrpc.newBlockingStub(it) }
    .required()

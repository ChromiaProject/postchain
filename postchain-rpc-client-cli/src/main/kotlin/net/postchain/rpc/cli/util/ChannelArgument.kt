package net.postchain.rpc.cli.util

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.grpc.ManagedChannelBuilder
import net.postchain.server.rpc.PostchainServiceGrpc

fun CliktCommand.channelOption() = option("-t", "--target", envvar = "POSTCHAIN_TARGET", help = "Target path for command. On the form host:port")
    .convert { ManagedChannelBuilder.forTarget(it).usePlaintext().build() }

fun CliktCommand.blockingChannelOption() = channelOption()
    .convert { PostchainServiceGrpc.newBlockingStub(it) }
    .required()

fun CliktCommand.acyncChannelOption() = channelOption()
    .convert { PostchainServiceGrpc.newStub(it) }
    .required()

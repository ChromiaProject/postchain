package net.postchain.rpc.cli.util

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import io.grpc.ManagedChannelBuilder
import net.postchain.rpc.client.PostchainRpcClient

fun CliktCommand.clientArgument() = argument("TARGET", help = "Target path for command. On the form host:port")
    .convert { ManagedChannelBuilder.forTarget(it).usePlaintext().build() }
    .convert { PostchainRpcClient(it) }

package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import io.grpc.StatusRuntimeException
import io.grpc.health.v1.HealthCheckRequest
import io.grpc.health.v1.HealthCheckResponse
import net.postchain.admin.cli.util.ChannelFactory
import net.postchain.admin.cli.util.DEFAULT_CHANNEL_FACTORY
import net.postchain.admin.cli.util.blockingHealthServiceChannelOption

class HealthCommand(channelFactory: ChannelFactory = DEFAULT_CHANNEL_FACTORY)
    : CliktCommand(help = "Check server health") {

    private val channel by blockingHealthServiceChannelOption(channelFactory)

    override fun run() {
        try {
            val request = HealthCheckRequest.newBuilder().setService("").build()

            val reply = channel.check(request)
            if (reply.status == HealthCheckResponse.ServingStatus.SERVING) {
                echo("Healthy")
            } else {
                echo("Unhealthy: ${reply.status}")
            }
        } catch (e: StatusRuntimeException) {
            throw PrintMessage("Failed with: ${e.message}", true)
        }
    }
}
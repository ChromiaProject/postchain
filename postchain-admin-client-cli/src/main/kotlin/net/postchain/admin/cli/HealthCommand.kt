package net.postchain.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import io.grpc.StatusRuntimeException
import io.grpc.health.v1.HealthCheckRequest
import io.grpc.health.v1.HealthCheckResponse
import net.postchain.admin.cli.util.blockingHealthServiceChannelOption

class HealthCommand : CliktCommand(help = "Check server health") {

    private val channel by blockingHealthServiceChannelOption()

    override fun run() {
        try {
            val request = HealthCheckRequest.newBuilder().setService("").build()

            val reply = channel.check(request)
            if (reply.status == HealthCheckResponse.ServingStatus.SERVING) {
                echo("Healthy")
            } else {
                throw CliktError("Unhealthy: ${reply.status}")
            }
        } catch (e: StatusRuntimeException) {
            throw CliktError("Failed with: ${e.message}")
        }
    }
}

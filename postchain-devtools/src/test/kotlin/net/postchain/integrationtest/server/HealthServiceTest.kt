package net.postchain.integrationtest.server

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.grpc.health.v1.HealthCheckRequest
import io.grpc.health.v1.HealthCheckResponse
import io.grpc.health.v1.HealthGrpc
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import net.postchain.server.grpc.HealthServiceGrpcImpl
import net.postchain.server.service.HealthService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HealthServiceTest : PostchainServerBase() {

    private lateinit var healthServiceStub: HealthGrpc.HealthBlockingStub

    @BeforeEach
    fun setup() {
        setupNode()
        setupHealthService()
    }

    private fun setupHealthService() {
        val healthServiceServerName = InProcessServerBuilder.generateName()
        grpcCleanupRule.register(
                InProcessServerBuilder.forName(healthServiceServerName)
                        .directExecutor()
                        .addService(HealthServiceGrpcImpl(HealthService(nodeProvider)))
                        .build()
                        .start()
        )
        healthServiceStub = HealthGrpc.newBlockingStub(
                grpcCleanupRule.register(
                        InProcessChannelBuilder.forName(healthServiceServerName)
                                .directExecutor()
                                .build()
                )
        )
    }

    @Test
    fun `Verify health`() {
        val healthReply = healthServiceStub.check(HealthCheckRequest.newBuilder().build())
        assertThat(healthReply.status).isEqualTo(HealthCheckResponse.ServingStatus.SERVING)
    }
}
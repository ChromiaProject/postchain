package net.postchain.admin.cli.testbase

import io.grpc.Channel
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import net.postchain.admin.cli.testutil.TestConsole
import net.postchain.server.grpc.HealthServiceGrpcImpl
import net.postchain.server.service.HealthService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.mock

abstract class HealthServiceCommandTestBase {

    @ExtendWith
    val testConsole = TestConsole()

    protected lateinit var healthService: HealthService
    private lateinit var channel: Channel
    private lateinit var server: Server

    @BeforeEach
    fun setup() {
        healthService = mock()
    }

    @AfterEach
    fun teardown() {
        (channel as? ManagedChannel)?.shutdownNow()
        server.shutdownNow()
    }

    protected fun setupChannel(healthService: HealthService): Channel {
        val healthServiceServerName = InProcessServerBuilder.generateName()
        server = InProcessServerBuilder.forName(healthServiceServerName)
                .directExecutor()
                .addService(HealthServiceGrpcImpl(healthService))
                .build()
                .start()
        channel = InProcessChannelBuilder.forName(healthServiceServerName)
                .directExecutor()
                .build()
        return channel
    }
}
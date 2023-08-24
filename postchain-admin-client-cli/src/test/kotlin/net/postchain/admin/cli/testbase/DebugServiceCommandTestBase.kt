package net.postchain.admin.cli.testbase

import io.grpc.Channel
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import net.postchain.admin.cli.testutil.TestConsole
import net.postchain.server.grpc.DebugServiceGrpcImpl
import net.postchain.server.service.DebugService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.mock

abstract class DebugServiceCommandTestBase {

    @ExtendWith
    val testConsole = TestConsole()

    protected lateinit var debugService: DebugService
    private lateinit var channel: Channel
    private lateinit var server: Server

    @BeforeEach
    fun setup() {
        debugService = mock()
    }

    @AfterEach
    fun teardown() {
        (channel as? ManagedChannel)?.shutdownNow()
        server.shutdownNow()
    }

    protected fun setupChannel(debugService: DebugService): Channel {
        val debugServiceServerName = InProcessServerBuilder.generateName()
        server = InProcessServerBuilder.forName(debugServiceServerName)
                .directExecutor()
                .addService(DebugServiceGrpcImpl(debugService))
                .build()
                .start()
        channel = InProcessChannelBuilder.forName(debugServiceServerName)
                .directExecutor()
                .build()
        return channel
    }
}
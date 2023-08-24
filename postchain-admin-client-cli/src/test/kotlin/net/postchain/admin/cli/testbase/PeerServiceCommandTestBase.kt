package net.postchain.admin.cli.testbase

import io.grpc.Channel
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import net.postchain.admin.cli.testutil.TestConsole
import net.postchain.server.grpc.PeerServiceGrpcImpl
import net.postchain.server.service.PeerService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.mock

abstract class PeerServiceCommandTestBase {

    @ExtendWith
    val testConsole = TestConsole()

    protected lateinit var peerService: PeerService
    private lateinit var channel: Channel
    private lateinit var server: Server

    @BeforeEach
    fun setup() {
        peerService = mock()
    }

    @AfterEach
    fun teardown() {
        (channel as? ManagedChannel)?.shutdownNow()
        server.shutdownNow()
    }

    protected fun setupChannel(peerService: PeerService): Channel {
        val peerServiceServerName = InProcessServerBuilder.generateName()
        server = InProcessServerBuilder.forName(peerServiceServerName)
                .directExecutor()
                .addService(PeerServiceGrpcImpl(peerService))
                .build()
                .start()
        channel = InProcessChannelBuilder.forName(peerServiceServerName)
                .directExecutor()
                .build()
        return channel
    }
}
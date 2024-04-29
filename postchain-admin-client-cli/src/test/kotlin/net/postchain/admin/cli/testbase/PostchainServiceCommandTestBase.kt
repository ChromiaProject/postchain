package net.postchain.admin.cli.testbase

import io.grpc.Channel
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import net.postchain.admin.cli.testutil.TestConsole
import net.postchain.server.grpc.PostchainServiceGrpcImpl
import net.postchain.server.service.PostchainService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.mock
import java.io.File
import java.nio.file.Paths

abstract class PostchainServiceCommandTestBase {

    @ExtendWith
    val testConsole = TestConsole()

    protected lateinit var postchainService: PostchainService
    private lateinit var channel: Channel
    private lateinit var server: Server

    @BeforeEach
    fun setup() {
        postchainService = mock()
    }

    @AfterEach
    fun teardown() {
        (channel as? ManagedChannel)?.shutdownNow()
        server.shutdownNow()
    }

    protected fun getResourceFile(name: String): File {
        return Paths.get(javaClass.getResource("/net/postchain/admin/cli/${name}")!!.toURI()).toFile()
    }

    protected fun setupChannel(postchainService: PostchainService): Channel {
        val postchainServiceServerName = InProcessServerBuilder.generateName()
        server = InProcessServerBuilder.forName(postchainServiceServerName)
                .directExecutor()
                .addService(PostchainServiceGrpcImpl(postchainService))
                .build()
                .start()
        channel = InProcessChannelBuilder.forName(postchainServiceServerName)
                .directExecutor()
                .build()
        return channel
    }
}
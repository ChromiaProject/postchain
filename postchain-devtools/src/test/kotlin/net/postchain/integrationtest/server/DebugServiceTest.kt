package net.postchain.integrationtest.server

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import net.postchain.server.grpc.DebugRequest
import net.postchain.server.grpc.DebugServiceGrpc
import net.postchain.server.grpc.DebugServiceGrpcImpl
import net.postchain.server.service.DebugService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DebugServiceTest : PostchainServerBase() {

    private lateinit var debugServiceStub: DebugServiceGrpc.DebugServiceBlockingStub

    @BeforeEach
    fun setup() {
        setupNode()
        setupDebugService()
    }

    private fun setupDebugService() {
        val debugServiceServerName = InProcessServerBuilder.generateName()
        grpcCleanupRule.register(
                InProcessServerBuilder.forName(debugServiceServerName)
                        .directExecutor()
                        .addService(DebugServiceGrpcImpl(DebugService(nodeProvider)))
                        .build()
                        .start()
        )
        debugServiceStub = DebugServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(
                        InProcessChannelBuilder.forName(debugServiceServerName)
                                .directExecutor()
                                .build()
                )
        )
    }

    @Test
    fun `Verify debug info`() {
        val debugReply = debugServiceStub.debugInfo(DebugRequest.newBuilder().build())
        println(debugReply.message)
        val jsonContext: DocumentContext = JsonPath.parse(debugReply.message)
        assertThat(jsonContext.read<String>("$['version']")).isEqualTo("null")
        assertThat(jsonContext.read<String>("$['pub-key']")).isEqualTo("03A301697BDFCD704313BA48E51D567543F2A182031EFD6915DDC07BBCC4E16070")
        assertThat(jsonContext.read<String>("$['infrastructure']")).isEqualTo("net.postchain.ebft.BaseEBFTInfrastructureFactory")
        assertThat(jsonContext.read<String>("$['blockchain'][0]['brid']")).isEqualTo("5A91BCEF349155B552DA0ABF48B8DC7C0E2389313A22D4BC9FBB16AB82A232B9")
        assertThat(jsonContext.read<String>("$['blockchain'][0]['node-type']")).isEqualTo("Validator")
    }
}
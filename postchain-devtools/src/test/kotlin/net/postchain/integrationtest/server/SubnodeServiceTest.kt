package net.postchain.integrationtest.server

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.google.protobuf.ByteString
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import net.postchain.crypto.PrivKey
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.devtools.TestLazyPostchainNodeProvider
import net.postchain.server.grpc.InitNodeRequest
import net.postchain.server.grpc.PostchainServiceGrpc
import net.postchain.server.grpc.StartSubnodeBlockchainRequest
import net.postchain.server.grpc.StopBlockchainRequest
import net.postchain.server.grpc.SubnodeServiceGrpc
import net.postchain.server.grpc.SubnodeServiceGrpcImpl
import net.postchain.server.service.SubnodeService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubnodeServiceTest : PostchainServerBase() {

    private lateinit var subnodeServiceStub: SubnodeServiceGrpc.SubnodeServiceBlockingStub
    private lateinit var postchainServiceStub: PostchainServiceGrpc.PostchainServiceBlockingStub
    private lateinit var lazyNodeProvider: TestLazyPostchainNodeProvider

    @BeforeEach
    fun setup() {
        setupLazyNodeProvider()
        setupSubNodeService()
        postchainServiceStub = setupPostchainService(lazyNodeProvider)
    }

    private fun setupLazyNodeProvider() {
        lazyNodeProvider = TestLazyPostchainNodeProvider {
            val nodes = createNodes(1, "/net/postchain/devtools/server/blockchain_config_1.xml")
            node = nodes[0]
            node
        }
    }

    private fun setupSubNodeService() {
        val subnodeServiceServerName = InProcessServerBuilder.generateName()
        grpcCleanupRule.register(
                InProcessServerBuilder.forName(subnodeServiceServerName)
                        .directExecutor()
                        .addService(SubnodeServiceGrpcImpl(SubnodeService(lazyNodeProvider)))
                        .build()
                        .start()
        )
        subnodeServiceStub = SubnodeServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(
                        InProcessChannelBuilder.forName(subnodeServiceServerName)
                                .directExecutor()
                                .build()
                )
        )
    }

    @Test
    fun `Initiate sub-node and start blockchain`() {
        val privKey = PrivKey(KeyPairHelper.privKey(1))
        val initNodeReply = subnodeServiceStub.initNode(
                InitNodeRequest.newBuilder()
                        .setPrivkey(ByteString.copyFrom(privKey.data))
                        .build()
        )
        assertThat(initNodeReply.message).isEqualTo("Postchain Node was initialized successfully")
        assertThat(lazyNodeProvider.privKey).isEqualTo(privKey)
        assertThat(lazyNodeProvider.wipeDb.get()).isEqualTo(false)

        val actualBrid = node.getBlockchainInstance(1L).blockchainEngine.getConfiguration().blockchainRid

        postchainServiceStub.stopBlockchain(
                StopBlockchainRequest.newBuilder()
                        .setChainId(1L)
                        .build()
        )
        assertThat(node.retrieveBlockchain(1L)).isNull()

        subnodeServiceStub.startSubnodeBlockchain(
                StartSubnodeBlockchainRequest.newBuilder()
                        .setChainId(1L)
                        .setBrid(ByteString.copyFrom(actualBrid.data))
                        .build()
        )
        assertThat(node.retrieveBlockchain(1L)).isNotNull()
    }
}
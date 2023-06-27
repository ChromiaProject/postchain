package net.postchain.integrationtest.server

import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.testing.GrpcCleanupRule
import net.postchain.devtools.ConfigFileBasedIntegrationTest
import net.postchain.devtools.PostchainTestNode
import net.postchain.server.NodeProvider
import net.postchain.server.grpc.AddPeerRequest
import net.postchain.server.grpc.PeerServiceGrpc
import net.postchain.server.grpc.PeerServiceGrpcImpl
import net.postchain.server.grpc.PostchainServiceGrpc
import net.postchain.server.grpc.PostchainServiceGrpcImpl
import net.postchain.server.service.PeerService
import net.postchain.server.service.PostchainService
import org.junit.Rule

abstract class PostchainServerBase : ConfigFileBasedIntegrationTest() {

    protected lateinit var node: PostchainTestNode
    protected lateinit var nodeProvider: NodeProvider

    @JvmField
    @Rule
    val grpcCleanupRule = GrpcCleanupRule()

    protected fun setupNode() {
        val nodes = createNodes(1, "/net/postchain/devtools/server/blockchain_config_1.xml")
        node = nodes[0]
        nodeProvider = NodeProvider { node }
    }

    protected fun setupPostchainService(nodeProvider: NodeProvider): PostchainServiceGrpc.PostchainServiceBlockingStub {
        val postchainServiceServerName = InProcessServerBuilder.generateName()
        grpcCleanupRule.register(
                InProcessServerBuilder.forName(postchainServiceServerName)
                        .directExecutor()
                        .addService(PostchainServiceGrpcImpl(PostchainService(nodeProvider)))
                        .build()
                        .start()
        )
        return PostchainServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(
                        InProcessChannelBuilder.forName(postchainServiceServerName)
                                .directExecutor()
                                .build()
                )
        )
    }

    protected fun setupPeerService(): PeerServiceGrpc.PeerServiceBlockingStub {
        val peerServiceServerName = InProcessServerBuilder.generateName()
        grpcCleanupRule.register(
                InProcessServerBuilder.forName(peerServiceServerName)
                        .directExecutor()
                        .addService(PeerServiceGrpcImpl(PeerService(nodeProvider)))
                        .build()
                        .start()
        )

        val peerServiceStub = PeerServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(
                        InProcessChannelBuilder.forName(peerServiceServerName)
                                .directExecutor()
                                .build()
                )
        )

        peerServiceStub.addPeer(
                AddPeerRequest.newBuilder()
                        .setPubkey(node.pubKey)
                        .setHost("localhost")
                        .setPort(7740)
                        .setOverride(true)
                        .build()
        )

        return peerServiceStub
    }
}
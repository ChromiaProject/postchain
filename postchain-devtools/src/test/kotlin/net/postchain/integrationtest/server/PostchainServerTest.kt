package net.postchain.integrationtest.server

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.testing.GrpcCleanupRule
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.toHex
import net.postchain.crypto.PubKey
import net.postchain.devtools.ConfigFileBasedIntegrationTest
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.getModules
import net.postchain.integrationtest.reconfiguration.DummyModule1
import net.postchain.integrationtest.reconfiguration.DummyModule2
import net.postchain.server.NodeProvider
import net.postchain.server.grpc.AddBlockchainReplicaRequest
import net.postchain.server.grpc.AddConfigurationRequest
import net.postchain.server.grpc.AddPeerRequest
import net.postchain.server.grpc.FindBlockchainRequest
import net.postchain.server.grpc.ListPeersRequest
import net.postchain.server.grpc.PeerServiceGrpc
import net.postchain.server.grpc.PeerServiceGrpcImpl
import net.postchain.server.grpc.PostchainServiceGrpc
import net.postchain.server.grpc.PostchainServiceGrpcImpl
import net.postchain.server.grpc.RemoveBlockchainReplicaRequest
import net.postchain.server.grpc.RemovePeerRequest
import net.postchain.server.grpc.StartBlockchainRequest
import net.postchain.server.grpc.StopBlockchainRequest
import net.postchain.server.service.PeerService
import net.postchain.server.service.PostchainService
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.Rule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PostchainServerTest : ConfigFileBasedIntegrationTest() {
    private lateinit var node: PostchainTestNode
    private lateinit var postchainServiceStub: PostchainServiceGrpc.PostchainServiceBlockingStub
    private lateinit var peerServiceStub: PeerServiceGrpc.PeerServiceBlockingStub

    @JvmField
    @Rule
    val grpcCleanupRule = GrpcCleanupRule()

    @BeforeEach
    fun setup() {
        val nodes = createNodes(1, "/net/postchain/devtools/server/blockchain_config_1.xml")
        node = nodes[0]
        val nodeProvider = NodeProvider { node }
        val postchainServiceServerName = InProcessServerBuilder.generateName()
        grpcCleanupRule.register(
                InProcessServerBuilder.forName(postchainServiceServerName)
                        .directExecutor()
                        .addService(PostchainServiceGrpcImpl(PostchainService(nodeProvider)))
                        .build()
                        .start()
        )
        val peerServiceServerName = InProcessServerBuilder.generateName()
        grpcCleanupRule.register(
                InProcessServerBuilder.forName(peerServiceServerName)
                        .directExecutor()
                        .addService(PeerServiceGrpcImpl(PeerService(nodeProvider)))
                        .build()
                        .start()
        )

        postchainServiceStub = PostchainServiceGrpc.newBlockingStub(
                grpcCleanupRule.register(
                        InProcessChannelBuilder.forName(postchainServiceServerName)
                                .directExecutor()
                                .build()
                )
        )
        peerServiceStub = PeerServiceGrpc.newBlockingStub(
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
                        .build()
        )
    }

    @Test
    fun `Stop and start blockchain via gRPC`() {
        // Ensure we can find the default test chain
        val actualBrid = node.getBlockchainInstance(1L).blockchainEngine.getConfiguration().blockchainRid
        val findBlockchainReply = postchainServiceStub.findBlockchain(
                FindBlockchainRequest.newBuilder()
                        .setChainId(1L)
                        .build()
        )
        assertThat(findBlockchainReply.brid).isEqualTo(actualBrid.toHex())

        // Stop
        postchainServiceStub.stopBlockchain(
                StopBlockchainRequest.newBuilder()
                        .setChainId(1L)
                        .build()
        )
        assertThat(node.retrieveBlockchain(1L)).isNull()

        // Start again
        postchainServiceStub.startBlockchain(
                StartBlockchainRequest.newBuilder()
                        .setChainId(1L)
                        .build()
        )
        assertThat(node.retrieveBlockchain(1L)).isNotNull()
    }

    @Test
    fun `Add configuration`() {
        assertThat(node.getModules(1L)[0]).isInstanceOf(DummyModule1::class)

        val configXml = javaClass.getResource("/net/postchain/devtools/server/blockchain_config_2.xml")!!.readText()
        postchainServiceStub.addConfiguration(
                AddConfigurationRequest.newBuilder()
                        .setChainId(1L)
                        .setHeight(1L)
                        .setXml(configXml)
                        .build()
        )

        // Build a block so config is applied
        buildBlock(0)

        // Await restart
        Awaitility.await().atMost(Duration.TEN_SECONDS).untilAsserted {
            assertThat(node.getModules()).isNotEmpty()
            assertThat(node.getModules(1L)[0]).isInstanceOf(DummyModule2::class)
        }
    }

    @Test
    fun `Add and remove a peer as a BC replica`() {
        val replicaKey = generatePubKey(-1).toHex()
        val brid = node.getBlockchainInstance(1L).blockchainEngine.getConfiguration().blockchainRid

        val listPeersReply = peerServiceStub.listPeers(ListPeersRequest.newBuilder().build())
        assertThat(listPeersReply.message.contains(replicaKey)).isFalse()

        // Add
        peerServiceStub.addPeer(
                AddPeerRequest.newBuilder()
                        .setPubkey(replicaKey)
                        .setHost("localhost")
                        .setPort(7741)
                        .build()
        )

        val listPeersReplyAfterAdd = peerServiceStub.listPeers(ListPeersRequest.newBuilder().build())
        assertThat(listPeersReplyAfterAdd.message).contains(replicaKey)

        postchainServiceStub.addBlockchainReplica(
                AddBlockchainReplicaRequest.newBuilder()
                        .setBrid(brid.toHex())
                        .setPubkey(replicaKey)
                        .build()
        )
        val replicaExistsAfterAdd = withReadConnection(node.getBlockchainInstance(1L).blockchainEngine.sharedStorage, 1L) {
            DatabaseAccess.of(it).existsBlockchainReplica(it, brid, PubKey(replicaKey))
        }
        assertThat(replicaExistsAfterAdd).isTrue()

        // Remove
        postchainServiceStub.removeBlockchainReplica(
                RemoveBlockchainReplicaRequest.newBuilder()
                        .setBrid(brid.toHex())
                        .setPubkey(replicaKey)
                        .build()
        )
        val replicaExistsAfterRemove = withReadConnection(node.getBlockchainInstance(1L).blockchainEngine.sharedStorage, 1L) {
            DatabaseAccess.of(it).existsBlockchainReplica(it, brid, PubKey(replicaKey))
        }
        assertThat(replicaExistsAfterRemove).isFalse()

        peerServiceStub.removePeer(
                RemovePeerRequest.newBuilder()
                        .setPubkey(replicaKey)
                        .build()
        )

        val listPeersReplyAfterRemove = peerServiceStub.listPeers(ListPeersRequest.newBuilder().build())
        assertThat(listPeersReplyAfterRemove.message.contains(replicaKey)).isFalse()
    }
}

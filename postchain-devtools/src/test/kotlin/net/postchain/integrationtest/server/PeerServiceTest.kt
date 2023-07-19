package net.postchain.integrationtest.server

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.toHex
import net.postchain.crypto.PubKey
import net.postchain.server.grpc.AddBlockchainReplicaRequest
import net.postchain.server.grpc.AddPeerRequest
import net.postchain.server.grpc.ListPeersRequest
import net.postchain.server.grpc.PeerServiceGrpc
import net.postchain.server.grpc.PostchainServiceGrpc
import net.postchain.server.grpc.RemoveBlockchainReplicaRequest
import net.postchain.server.grpc.RemovePeerRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PeerServiceTest : PostchainServerBase() {

    private lateinit var postchainServiceStub: PostchainServiceGrpc.PostchainServiceBlockingStub
    private lateinit var peerServiceStub: PeerServiceGrpc.PeerServiceBlockingStub

    @BeforeEach
    fun setup() {
        setupNode()
        postchainServiceStub = setupPostchainService(nodeProvider)
        peerServiceStub = setupPeerService()
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
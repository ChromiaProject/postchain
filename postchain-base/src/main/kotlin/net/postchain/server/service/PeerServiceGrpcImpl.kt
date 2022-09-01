package net.postchain.server.service

import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import net.postchain.common.exception.AlreadyExists
import net.postchain.common.hexStringToByteArray
import net.postchain.service.PeerService

class PeerServiceGrpcImpl(private val peerService: PeerService) : PeerServiceGrpc.PeerServiceImplBase() {

    override fun addPeer(request: AddPeerRequest, responseObserver: StreamObserver<AddPeerReply>) {
        val pubkey = request.pubkey
        verifyPubKey(pubkey)?.let { return responseObserver.onError(it) }

        try {
            peerService.addPeer(request.pubkey, request.host, request.port, request.override)
            responseObserver.onNext(
                AddPeerReply.newBuilder()
                    .setMessage("Peer was added successfully")
                    .build()
            )
            responseObserver.onCompleted()
        } catch (e: AlreadyExists) {
            responseObserver.onError(
                Status.ALREADY_EXISTS.withDescription(e.message).asRuntimeException()
            )
        }
    }

    override fun removePeer(request: RemovePeerRequest, responseObserver: StreamObserver<RemovePeerReply>) {
        val pubkey = request.pubkey
        verifyPubKey(pubkey)?.let { return responseObserver.onError(it) }
        val removedPeer = peerService.removePeer(pubkey)

        val message = if (removedPeer.isEmpty()) {
            "No peer has been removed"
        } else {
            "Successfully removed peer: ${removedPeer[0]}"
        }
        responseObserver.onNext(
            RemovePeerReply.newBuilder()
                .setMessage(message)
                .build()
        )
        responseObserver.onCompleted()
    }

    private fun verifyPubKey(pubkey: String): StatusRuntimeException? {
        if (pubkey.length != 66) {
            return Status.INVALID_ARGUMENT.withDescription("Public key $pubkey must be of length 66, but was ${pubkey.length}")
                .asRuntimeException()
        }
        try {
            pubkey.hexStringToByteArray()
        } catch (e: Exception) {
            return Status.INVALID_ARGUMENT.withDescription("Public key $pubkey must be a valid hex string")
                .asRuntimeException()
        }
        return null
    }

    override fun listPeers(request: ListPeersRequest, responseObserver: StreamObserver<ListPeersReply>) {
        val peers = peerService.listPeers()

        val message = if (peers.isEmpty()) {
            "No peers found"
        } else {
            peers.joinToString(
                "\n",
                "Peers (${peers.size}):\n"
            )
        }
        responseObserver.onNext(
            ListPeersReply.newBuilder()
                .setMessage(message)
                .build()
        )
        responseObserver.onCompleted()
    }
}

package net.postchain.server.grpc

import io.grpc.Status
import io.grpc.stub.StreamObserver
import net.postchain.common.exception.AlreadyExists
import net.postchain.crypto.PubKey
import net.postchain.server.service.PeerService

class PeerServiceGrpcImpl(private val peerService: PeerService) : PeerServiceGrpc.PeerServiceImplBase() {

    override fun addPeer(request: AddPeerRequest, responseObserver: StreamObserver<AddPeerReply>) {
        try {
            if (peerService.addPeer(PubKey(request.pubkey), request.host, request.port, request.override)) {
                responseObserver.onNext(
                        AddPeerReply.newBuilder()
                                .setMessage("Peer was added successfully")
                                .build()
                )
                responseObserver.onCompleted()
            } else {
                responseObserver.onError(
                        Status.ALREADY_EXISTS.withDescription("public key already added for a host, use override to add anyway").asRuntimeException()
                )
            }
        } catch (e: IllegalArgumentException) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT.withDescription(e.message).asRuntimeException()
            )
        } catch (e: AlreadyExists) {
            responseObserver.onError(
                    Status.ALREADY_EXISTS.withDescription(e.message).asRuntimeException()
            )
        }
    }

    override fun removePeer(request: RemovePeerRequest, responseObserver: StreamObserver<RemovePeerReply>) {
        try {
            val removedPeer = peerService.removePeer(PubKey(request.pubkey))

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
        } catch (e: IllegalArgumentException) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT.withDescription(e.message).asRuntimeException()
            )
        }
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

package net.postchain.server.service

import io.grpc.Status
import io.grpc.stub.StreamObserver
import net.postchain.PostchainContext
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withWriteConnection
import net.postchain.common.hexStringToByteArray

class PeerService(private val postchainContext: PostchainContext) : PeerServiceGrpc.PeerServiceImplBase() {

    override fun addPeer(request: AddPeerRequest?, responseObserver: StreamObserver<AddPeerReply>?) {
        val pubkey = request!!.pubkey
        if (pubkey.length != 66) {
            return responseObserver?.onError(
                Status.INVALID_ARGUMENT.withDescription("Public key $pubkey must be of length 66, but was ${pubkey.length}")
                    .asRuntimeException()
            )!!
        }
        try {
            pubkey.hexStringToByteArray()
        } catch (e: java.lang.Exception) {
            return responseObserver?.onError(
                Status.INVALID_ARGUMENT.withDescription("Public key $pubkey must be a valid hex string")
                    .asRuntimeException()
            )!!
        }
        postchainContext.storage.withWriteConnection { ctx ->
            val db = DatabaseAccess.of(ctx)
            val targetHost = db.findPeerInfo(ctx, request.host, request.port, null)
            if (targetHost.isNotEmpty()) {
                return@withWriteConnection responseObserver?.onError(
                    Status.ALREADY_EXISTS.withDescription("Peer already exists on current host").asRuntimeException()
                )
            }
            val targetKey = db.findPeerInfo(ctx, null, null, pubkey)
            if (targetKey.isNotEmpty() && !request.override) {
                return@withWriteConnection responseObserver?.onError(
                    Status.ALREADY_EXISTS.withDescription("public key already added for a host, use override to add anyway")
                        .asRuntimeException()
                )
            }
            db.addPeerInfo(ctx, request.host, request.port, pubkey)
            responseObserver?.onNext(
                AddPeerReply.newBuilder()
                    .setMessage("Peer was added successfully")
                    .build()
            )
            responseObserver?.onCompleted()
        }
    }

    override fun removePeer(request: RemovePeerRequest?, responseObserver: StreamObserver<RemovePeerReply>?) {
        val pubkey = request!!.pubkey
        val removedPeer = postchainContext.storage.withWriteConnection { ctx ->
            DatabaseAccess.of(ctx).removePeerInfo(ctx, pubkey)
        }

        val message = if (removedPeer.isEmpty()) {
            "No peer has been removed"
        } else {
            "Successfully removed peer: ${removedPeer[0]}"
        }
        responseObserver?.onNext(
            RemovePeerReply.newBuilder()
                .setMessage(message)
                .build()
        )
        responseObserver?.onCompleted()
    }

    override fun listPeers(request: ListPeersRequest?, responseObserver: StreamObserver<ListPeersReply>?) {
        val peers = postchainContext.storage.withWriteConnection { ctx ->
            DatabaseAccess.of(ctx).findPeerInfo(ctx, null, null, null)
        }

        val message = if (peers.isEmpty()) {
            "No peers found"
        } else {
            peers.joinToString(
                "\n",
                "Peers (${peers.size}):\n"
            )
        }
        responseObserver?.onNext(
            ListPeersReply.newBuilder()
                .setMessage(message)
                .build()
        )
        responseObserver?.onCompleted()
    }
}
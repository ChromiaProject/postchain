package net.postchain.server.grpc

import io.grpc.Status
import io.grpc.stub.StreamObserver
import net.postchain.common.exception.AlreadyExists
import net.postchain.crypto.PrivKey
import net.postchain.server.LazyPostchainNodeProvider

class InitServiceGrpcImpl(private val nodeProvider: LazyPostchainNodeProvider) : InitServiceGrpc.InitServiceImplBase() {

    override fun initNode(request: InitNodeRequest, responseObserver: StreamObserver<InitNodeReply>) {
        try {
            nodeProvider.init(PrivKey(request.privkey.toByteArray()), false)
            responseObserver.onNext(
                    InitNodeReply.newBuilder()
                            .setMessage("Postchain Node was initialized successfully")
                            .build()
            )
            responseObserver.onCompleted()
        } catch (e: AlreadyExists) {
            responseObserver.onError(
                    Status.ALREADY_EXISTS.withDescription(e.message).asRuntimeException()
            )
        } catch (e: Exception) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription(e.message).asRuntimeException()
            )
        }
    }
}
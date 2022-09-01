package net.postchain.server.service

import io.grpc.stub.StreamObserver

class DebugServiceGrpcImpl(private val debugService: DebugService) : DebugServiceGrpc.DebugServiceImplBase() {

    override fun debugService(request: DebugRequest?, responseObserver: StreamObserver<DebugReply>?) {
        val message = debugService.debugService()
        responseObserver?.onNext(
            DebugReply.newBuilder()
                .setMessage(message)
                .build()
        )
        responseObserver?.onCompleted()
    }
}

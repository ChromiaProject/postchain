package net.postchain.server.service

import io.grpc.stub.StreamObserver
import net.postchain.service.DebugService

class DebugServiceGrpcImpl(private val debugService: DebugService) : DebugServiceGrpc.DebugServiceImplBase() {

    override fun debugInfo(request: DebugRequest, responseObserver: StreamObserver<DebugReply>) {
        val message = debugService.debugInfo()
        responseObserver.onNext(
            DebugReply.newBuilder()
                .setMessage(message)
                .build()
        )
        responseObserver.onCompleted()
    }
}

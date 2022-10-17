package net.postchain.server.grpc

import io.grpc.stub.StreamObserver
import net.postchain.server.service.DebugService

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

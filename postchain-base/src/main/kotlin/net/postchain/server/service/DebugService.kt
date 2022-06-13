package net.postchain.server.service

import com.google.gson.JsonObject
import io.grpc.stub.StreamObserver
import net.postchain.api.rest.json.JsonFactory
import net.postchain.debug.NodeDiagnosticContext

class DebugService(private val nodeDiagnosticContext: NodeDiagnosticContext?) : DebugServiceGrpc.DebugServiceImplBase() {

    private val jsonBuilder = JsonFactory.makePrettyJson()

    override fun debugService(request: DebugRequest?, responseObserver: StreamObserver<DebugReply>?) {
        val message = if (nodeDiagnosticContext == null) {
            "Debug mode is not enabled"
        } else {
            JsonObject()
                .apply {
                    nodeDiagnosticContext.getProperties().forEach { (property, value) ->
                        add(property, jsonBuilder.toJsonTree(value))
                    }
                }.let(jsonBuilder::toJson)
        }

        responseObserver?.onNext(
            DebugReply.newBuilder()
                .setMessage(message)
                .build()
        )
        responseObserver?.onCompleted()
    }
}
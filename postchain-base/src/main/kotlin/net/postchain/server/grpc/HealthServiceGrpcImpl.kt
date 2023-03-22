package net.postchain.server.grpc

import io.grpc.Status
import io.grpc.health.v1.HealthCheckRequest
import io.grpc.health.v1.HealthCheckResponse
import io.grpc.health.v1.HealthGrpc.HealthImplBase
import io.grpc.protobuf.services.HealthStatusManager
import io.grpc.stub.StreamObserver
import mu.KLogging
import net.postchain.server.service.HealthService

class HealthServiceGrpcImpl(private val healthService: HealthService) : HealthImplBase() {
    companion object : KLogging()

    override fun check(request: HealthCheckRequest, responseObserver: StreamObserver<HealthCheckResponse>) {
        val service = request.service
        if (service != HealthStatusManager.SERVICE_NAME_ALL_SERVICES) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("health check of service $service not supported").asRuntimeException())
        } else {
            try {
                healthService.healthCheck()
                responseObserver.onNext(HealthCheckResponse.newBuilder().setStatus(HealthCheckResponse.ServingStatus.SERVING).build())
                responseObserver.onCompleted()
            } catch (e: Exception) {
                logger.warn("Failed health check: $e")
                responseObserver.onNext(HealthCheckResponse.newBuilder().setStatus(HealthCheckResponse.ServingStatus.NOT_SERVING).build())
                responseObserver.onCompleted()
            }
        }
    }
}

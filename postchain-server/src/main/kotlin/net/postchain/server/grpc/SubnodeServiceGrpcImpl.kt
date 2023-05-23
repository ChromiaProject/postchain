package net.postchain.server.grpc

import io.grpc.Status
import io.grpc.stub.StreamObserver
import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.AlreadyExists
import net.postchain.crypto.PrivKey
import net.postchain.server.service.SubnodeService

class SubnodeServiceGrpcImpl(private val subnodeService: SubnodeService) : SubnodeServiceGrpc.SubnodeServiceImplBase() {

    companion object : KLogging()

    override fun initNode(request: InitNodeRequest, responseObserver: StreamObserver<InitNodeReply>) {
        try {
            subnodeService.initNode(PrivKey(request.privkey.toByteArray()))
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
            logger.warn(e) { "Unable to initialize PostchainNode: $e.message}" }
            responseObserver.onError(
                    Status.INTERNAL.withDescription(e.message).asRuntimeException()
            )
        }
    }

    override fun startSubnodeBlockchain(request: StartSubnodeBlockchainRequest, responseObserver: StreamObserver<StartSubnodeBlockchainReply>) {
        try {
            subnodeService.startBlockchain(request.chainId, BlockchainRid(request.brid.toByteArray()))
            responseObserver.onNext(
                    StartSubnodeBlockchainReply.newBuilder()
                            .setMessage("Blockchain was started successfully")
                            .build()
            )
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription(e.message).asRuntimeException()
            )
        }
    }
}
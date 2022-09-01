package net.postchain.server.service

import io.grpc.Status
import io.grpc.stub.StreamObserver
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.server.AlreadyExists
import net.postchain.server.NotFound

class PostchainServiceGrpcImpl(private val postchainService: PostchainService) :
    PostchainServiceGrpc.PostchainServiceImplBase() {

    override fun startBlockchain(
        request: StartBlockchainRequest,
        responseObserver: StreamObserver<StartBlockchainReply>,
    ) {
        try {
            val brid = postchainService.startBlockchain(request.chainId)
            responseObserver.onNext(
                StartBlockchainReply.newBuilder()
                    .setMessage("Blockchain with id ${request.chainId} started with brid $brid")
                    .build()
            )
            responseObserver.onCompleted()
        } catch (e: NotFound) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.message).asRuntimeException())
        }
    }

    override fun stopBlockchain(
        request: StopBlockchainRequest,
        responseObserver: StreamObserver<StopBlockchainReply>,
    ) {
        postchainService.stopBlockchain(request.chainId)
        responseObserver.onNext(
            StopBlockchainReply.newBuilder().run {
                message = "Blockchain has been stopped"
                build()
            })
        responseObserver.onCompleted()
    }

    override fun addConfiguration(
        request: AddConfigurationRequest,
        responseObserver: StreamObserver<AddConfigurationReply>,
    ) {
        val config = when (request.configCase) {
            AddConfigurationRequest.ConfigCase.XML -> GtvMLParser.parseGtvML(request.xml)
            AddConfigurationRequest.ConfigCase.GTV -> GtvDecoder.decodeGtv(request.gtv.toByteArray())
            else -> return responseObserver.onError(
                Status.INVALID_ARGUMENT.withDescription("Both xml and gtv fields are empty").asRuntimeException()
            )
        }

        try {
            postchainService.addConfiguration(request.chainId, request.height, request.override, config)
            responseObserver.onNext(
                AddConfigurationReply.newBuilder().run {
                    message = "Configuration height ${request.height} on chain ${request.chainId} has been added"
                    build()
                }
            )
            responseObserver.onCompleted()
        } catch (e: AlreadyExists) {
            responseObserver.onError(
                Status.ALREADY_EXISTS.withDescription(e.message).asRuntimeException()
            )
        }
    }

    override fun initializeBlockchain(
        request: InitializeBlockchainRequest,
        responseObserver: StreamObserver<InitializeBlockchainReply>,
    ) {
        val config = when (request.configCase) {
            InitializeBlockchainRequest.ConfigCase.XML -> GtvMLParser.parseGtvML(request.xml)
            InitializeBlockchainRequest.ConfigCase.GTV -> GtvDecoder.decodeGtv(request.gtv.toByteArray())
            else -> return responseObserver.onError(
                Status.INVALID_ARGUMENT.withDescription("Both xml and gtv fields are empty").asRuntimeException()
            )
        }
        val maybeBrid = when {
            request.hasBrid() -> BlockchainRid.buildFromHex(request.brid)
            else -> null
        }

        try {
            val blockchainRid =
                postchainService.initializeBlockchain(request.chainId, maybeBrid, request.override, config)
            responseObserver.onNext(InitializeBlockchainReply.newBuilder().run {
                message = "Blockchain has been initialized with blockchain RID: $blockchainRid"
                brid = blockchainRid.toHex()
                build()
            })
            responseObserver.onCompleted()
        } catch (e: AlreadyExists) {
            responseObserver.onError(
                Status.ALREADY_EXISTS.withDescription(e.message).asRuntimeException()
            )
        } catch (e: UserMistake) {
            responseObserver.onError(
                Status.INTERNAL.withDescription(e.message).asRuntimeException()
            )
        }
    }

    override fun findBlockchain(
        request: FindBlockchainRequest,
        responseObserver: StreamObserver<FindBlockchainReply>,
    ) {
        val response = postchainService.findBlockchain(request.chainId)

        responseObserver.onNext(FindBlockchainReply.newBuilder().run {
            this.brid = response?.first?.toHex() ?: ""
            this.active = response?.second ?: false
            build()
        })
        responseObserver.onCompleted()
    }

    override fun addBlockchainReplica(
        request: AddBlockchainReplicaRequest, responseObserver: StreamObserver<AddBlockchainReplicaReply>
    ) {
        try {
            postchainService.addBlockchainReplica(request.brid, request.pubkey)
            responseObserver.onNext(AddBlockchainReplicaReply.newBuilder().run {
                message = "Node ${request.pubkey} has been added as a replica for chain with brid ${request.brid}"
                build()
            })
            responseObserver.onCompleted()
        } catch (e: NotFound) {
            responseObserver.onError(
                Status.NOT_FOUND.withDescription(e.message).asRuntimeException()
            )
        }
    }

    override fun removeBlockchainReplica(
        request: RemoveBlockchainReplicaRequest, responseObserver: StreamObserver<RemoveBlockchainReplicaReply>
    ) {
        val removedReplica = postchainService.removeBlockchainReplica(request.brid, request.pubkey)
        val message = if (removedReplica.isEmpty()) {
            "No replica has been removed"
        } else {
            "Successfully removed replica: $removedReplica"
        }
        responseObserver.onNext(
            RemoveBlockchainReplicaReply.newBuilder().setMessage(message).build()
        )
        responseObserver.onCompleted()
    }
}

package net.postchain.server.service

import io.grpc.Status
import io.grpc.stub.StreamObserver
import net.postchain.PostchainNode
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DependenciesValidator
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.withWriteConnection
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.gtvml.GtvMLParser

class PostchainService(private val postchainNode: PostchainNode) : PostchainServiceGrpc.PostchainServiceImplBase() {

    override fun startBlockchain(
        request: StartBlockchainRequest?,
        responseObserver: StreamObserver<StartBlockchainReply>?
    ) {
        postchainNode.startBlockchain(request!!.chainId)
        responseObserver?.onNext(
            StartBlockchainReply.newBuilder()
                .setMessage("Blockchain with id ${request.chainId} started")
                .build()
        )
        responseObserver?.onCompleted()
    }

    override fun stopBlockchain(
        request: StopBlockchainRequest?,
        responseObserver: StreamObserver<StopBlockchainReply>?
    ) {
        postchainNode.stopBlockchain(request!!.chainId)
        responseObserver?.onNext(
            StopBlockchainReply.newBuilder().run {
                message = "Blockchain has been stopped"
                build()
            })
        responseObserver?.onCompleted()
    }

    override fun  addConfiguration(
        request: AddConfigurationRequest?,
        responseObserver: StreamObserver<AddConfigurationReply>?
    ) {
        val config = when (request!!.configCase) {
            AddConfigurationRequest.ConfigCase.XML -> GtvMLParser.parseGtvML(request.xml)
            AddConfigurationRequest.ConfigCase.GTV -> GtvDecoder.decodeGtv(request.gtv.toByteArray())
            else -> return responseObserver?.onError(Status.INVALID_ARGUMENT.withDescription("Both xml and gtv fields are empty").asRuntimeException())!!
        }
        withWriteConnection(postchainNode.postchainContext.storage, request.chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            val hasConfig = db.getConfigurationData(ctx, request.height) != null
            if (hasConfig && !request.override) {
                responseObserver?.onError(Status.ALREADY_EXISTS.withDescription("Configuration already exists for height ${request.height} on chain ${request.chainId}").asRuntimeException())
                return@withWriteConnection false
            }

            db.addConfigurationData(ctx, request.height, GtvEncoder.encodeGtv(config))
            true
        }
        responseObserver?.onNext(
            AddConfigurationReply.newBuilder().run {
                message = "Configuration height ${request.height} on chain ${request.chainId} has been added"
                build()
            }
        )
        responseObserver?.onCompleted()
    }

    override fun initializeBlockchain(
        request: InitializeBlockchainRequest?,
        responseObserver: StreamObserver<InitializeBlockchainReply>?
    ) {
        val config = when (request!!.configCase) {
            InitializeBlockchainRequest.ConfigCase.XML -> GtvMLParser.parseGtvML(request.xml)
            InitializeBlockchainRequest.ConfigCase.GTV -> GtvDecoder.decodeGtv(request.gtv.toByteArray())
            else -> return responseObserver?.onError(Status.INVALID_ARGUMENT.withDescription("Both xml and gtv fields are empty").asRuntimeException())!!
        }
        withWriteConnection(postchainNode.postchainContext.storage, request.chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            if (db.getBlockchainRid(ctx) != null && !request.override) {
                responseObserver?.onError(Status.ALREADY_EXISTS.withDescription("Blockchain already exists").asRuntimeException())
                return@withWriteConnection false
            }

            val brid = GtvToBlockchainRidFactory.calculateBlockchainRid(config)
            db.initializeBlockchain(ctx, brid)
            DependenciesValidator.validateBlockchainRids(ctx, listOf())
            // TODO: Blockchain dependencies [DependenciesValidator#validateBlockchainRids]
            db.addConfigurationData(ctx, 0, GtvEncoder.encodeGtv(config))
            true
        }
        postchainNode.startBlockchain(request.chainId)
        responseObserver?.onNext(InitializeBlockchainReply.newBuilder().run {
            message = "Blockchain has been initialized"
            build()
        })
        responseObserver?.onCompleted()
    }
}

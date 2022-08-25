package net.postchain.server.service

import io.grpc.Status
import io.grpc.stub.StreamObserver
import net.postchain.PostchainNode
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DependenciesValidator
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.gtvml.GtvMLParser

class PostchainService(private val postchainNode: PostchainNode) : PostchainServiceGrpc.PostchainServiceImplBase() {

    override fun startBlockchain(
            request: StartBlockchainRequest?,
            responseObserver: StreamObserver<StartBlockchainReply>?,
    ) {
        val brid = postchainNode.startBlockchain(request!!.chainId)
        if (brid == null) {
            responseObserver?.onError(Status.CANCELLED.withDescription("Blockchain with id ${request.chainId} not found in db.").asRuntimeException())
        } else {
            responseObserver?.onNext(
                    StartBlockchainReply.newBuilder()
                            .setMessage("Blockchain with id ${request.chainId} started with brid $brid")
                            .build()
            )
            responseObserver?.onCompleted()
        }
    }

    override fun stopBlockchain(
            request: StopBlockchainRequest?,
            responseObserver: StreamObserver<StopBlockchainReply>?,
    ) {
        postchainNode.stopBlockchain(request!!.chainId)
        responseObserver?.onNext(
                StopBlockchainReply.newBuilder().run {
                    message = "Blockchain has been stopped"
                    build()
                })
        responseObserver?.onCompleted()
    }

    override fun addConfiguration(
            request: AddConfigurationRequest?,
            responseObserver: StreamObserver<AddConfigurationReply>?,
    ) {
        val config = when (request!!.configCase) {
            AddConfigurationRequest.ConfigCase.XML -> GtvMLParser.parseGtvML(request.xml)
            AddConfigurationRequest.ConfigCase.GTV -> GtvDecoder.decodeGtv(request.gtv.toByteArray())
            else -> return responseObserver?.onError(
                    Status.INVALID_ARGUMENT.withDescription("Both xml and gtv fields are empty").asRuntimeException()
            )!!
        }
        val success = withWriteConnection(postchainNode.postchainContext.storage, request.chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            val hasConfig = db.getConfigurationData(ctx, request.height) != null
            if (hasConfig && !request.override) {
                responseObserver?.onError(
                        Status.ALREADY_EXISTS.withDescription("Configuration already exists for height ${request.height} on chain ${request.chainId}")
                                .asRuntimeException()
                )
                return@withWriteConnection false
            }

            db.addConfigurationData(ctx, request.height, GtvEncoder.encodeGtv(config))
            true
        }
        if (!success) return
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
            responseObserver: StreamObserver<InitializeBlockchainReply>?,
    ) {
        val config = when (request!!.configCase) {
            InitializeBlockchainRequest.ConfigCase.XML -> GtvMLParser.parseGtvML(request.xml)
            InitializeBlockchainRequest.ConfigCase.GTV -> GtvDecoder.decodeGtv(request.gtv.toByteArray())
            else -> return responseObserver?.onError(
                    Status.INVALID_ARGUMENT.withDescription("Both xml and gtv fields are empty").asRuntimeException()
            )!!
        }
        val success = withWriteConnection(postchainNode.postchainContext.storage, request.chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            if (db.getBlockchainRid(ctx) != null && !request.override) {
                responseObserver?.onError(
                        Status.ALREADY_EXISTS.withDescription("Blockchain already exists").asRuntimeException()
                )
                return@withWriteConnection false
            }

            val brid = when {
                request.hasBrid() -> BlockchainRid.buildFromHex(request.brid)
                else -> GtvToBlockchainRidFactory.calculateBlockchainRid(config)
            }

            db.initializeBlockchain(ctx, brid)
            DependenciesValidator.validateBlockchainRids(ctx, listOf())
            // TODO: Blockchain dependencies [DependenciesValidator#validateBlockchainRids]
            db.addConfigurationData(ctx, 0, GtvEncoder.encodeGtv(config))
            true
        }
        if (!success) return
        val blockchainRid = postchainNode.startBlockchain(request.chainId)
        responseObserver?.onNext(InitializeBlockchainReply.newBuilder().run {
            message = "Blockchain has been initialized with blockchain RID: $blockchainRid"
            brid = blockchainRid?.toHex() ?: ""
            build()
        })
        responseObserver?.onCompleted()
    }

    override fun findBlockchain(
            request: FindBlockchainRequest?,
            responseObserver: StreamObserver<FindBlockchainReply>?,
    ) {
        val brid = withReadConnection(postchainNode.postchainContext.storage, request!!.chainId) {
            DatabaseAccess.of(it).getBlockchainRid(it)
        }
        val active = postchainNode.isBlockchainRunning(request.chainId)

        responseObserver?.onNext(FindBlockchainReply.newBuilder().run {
            this.brid = brid?.toHex() ?: ""
            this.active = active
            build()
        })
        responseObserver?.onCompleted()
    }

    override fun addBlockchainReplica(
        request: AddBlockchainReplicaRequest?, responseObserver: StreamObserver<AddBlockchainReplicaReply>?
    ) {
        val success = postchainNode.postchainContext.storage.withWriteConnection { ctx ->
            val db = DatabaseAccess.of(ctx)

            val foundInPeerInfo = db.findPeerInfo(ctx, null, null, request!!.pubkey)
            if (foundInPeerInfo.isEmpty()) {
                responseObserver?.onError(
                    Status.NOT_FOUND.withDescription("Given pubkey is not a peer. First add it as a peer.")
                        .asRuntimeException()
                )
                return@withWriteConnection false
            }

            db.addBlockchainReplica(ctx, request.brid, request.pubkey)
            true
        }

        if (!success) return
        responseObserver?.onNext(AddBlockchainReplicaReply.newBuilder().run {
            message = "Node ${request!!.pubkey} has been added as a replica for chain with brid ${request.brid}"
            build()
        })
        responseObserver?.onCompleted()
    }

    override fun removeBlockchainReplica(
        request: RemoveBlockchainReplicaRequest?, responseObserver: StreamObserver<RemoveBlockchainReplicaReply>?
    ) {
        postchainNode.postchainContext.storage.withWriteConnection { ctx ->
            val db = DatabaseAccess.of(ctx)

            val removedReplica = db.removeBlockchainReplica(ctx, request!!.brid, request.pubkey)

            val message = if (removedReplica.isEmpty()) {
                "No replica has been removed"
            } else {
                "Successfully removed replica: $removedReplica"
            }
            responseObserver?.onNext(
                RemoveBlockchainReplicaReply.newBuilder().setMessage(message).build()
            )
            responseObserver?.onCompleted()
        }
    }
}

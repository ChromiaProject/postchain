package net.postchain.server.grpc

import com.google.protobuf.ByteString
import io.grpc.Status
import io.grpc.stub.StreamObserver
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.NotFound
import net.postchain.common.exception.UserMistake
import net.postchain.core.BadDataException
import net.postchain.crypto.PubKey
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.server.service.PostchainService
import java.nio.file.Path

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
        } catch (e: UserMistake) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.message).asRuntimeException())
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
            val added = postchainService.addConfiguration(request.chainId, request.height, request.override, config, request.allowUnknownSigners)
            if (added) {
                responseObserver.onNext(
                        AddConfigurationReply.newBuilder().run {
                            message = "Configuration height ${request.height} on chain ${request.chainId} has been added"
                            build()
                        }
                )
                responseObserver.onCompleted()
            } else {
                responseObserver.onError(
                        Status.ALREADY_EXISTS.withDescription(
                                "Configuration already exists for height ${request.height} on chain ${request.chainId}"
                        ).asRuntimeException()
                )
            }
        } catch (e: IllegalStateException) {
            responseObserver.onError(
                    Status.FAILED_PRECONDITION.withDescription(e.message).asRuntimeException()
            )
        } catch (e: BadDataException) {
            responseObserver.onError(
                    Status.FAILED_PRECONDITION.withDescription(e.message).asRuntimeException()
            )
        }
    }

    override fun listConfigurations(
            request: ListConfigurationsRequest,
            responseObserver: StreamObserver<ListConfigurationsReply>
    ) {
        if (postchainService.findBlockchain(request.chainId).first != null) {
            responseObserver.onNext(
                    ListConfigurationsReply.newBuilder().run {
                        addAllHeight(postchainService.listConfigurations(request.chainId))
                        build()
                    }
            )
            responseObserver.onCompleted()
        } else {
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription("Blockchain not found: ${request.chainId}")
                            .asRuntimeException()
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
            val initialized = postchainService.initializeBlockchain(request.chainId, maybeBrid, request.override, config) != null
            if (initialized) {
                val blockchainRid = postchainService.startBlockchain(request.chainId)
                responseObserver.onNext(InitializeBlockchainReply.newBuilder().run {
                    message = "Blockchain has been initialized with blockchain RID: $blockchainRid"
                    brid = blockchainRid.toHex()
                    build()
                })
                responseObserver.onCompleted()
            } else {
                responseObserver.onError(
                        Status.ALREADY_EXISTS.withDescription("Blockchain already exists").asRuntimeException()
                )
            }
        } catch (e: NotFound) {
            responseObserver.onError(
                    Status.NOT_FOUND.withDescription(e.message).asRuntimeException()
            )
        } catch (e: UserMistake) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription(e.message).asRuntimeException()
            )
        } catch (e: PostchainService.InitializationError) {
            responseObserver.onError(
                    Status.CANCELLED.withDescription(e.message).asRuntimeException()
            )

        }
    }

    override fun findBlockchain(
            request: FindBlockchainRequest,
            responseObserver: StreamObserver<FindBlockchainReply>,
    ) {
        val response = postchainService.findBlockchain(request.chainId)

        responseObserver.onNext(FindBlockchainReply.newBuilder().run {
            this.brid = response.first?.toHex() ?: ""
            this.active = response.second ?: false
            this.height = response.third
            build()
        })
        responseObserver.onCompleted()
    }

    override fun addBlockchainReplica(
            request: AddBlockchainReplicaRequest, responseObserver: StreamObserver<AddBlockchainReplicaReply>
    ) {
        try {
            postchainService.addBlockchainReplica(BlockchainRid.buildFromHex(request.brid), PubKey(request.pubkey))
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
        val removedReplica = postchainService.removeBlockchainReplica(BlockchainRid.buildFromHex(request.brid), PubKey(request.pubkey))
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

    override fun exportBlockchain(request: ExportBlockchainRequest, responseObserver: StreamObserver<ExportBlockchainReply>) {
        try {
            val exportResult = postchainService.exportBlockchain(
                    request.chainId,
                    Path.of(request.configurationsFile),
                    if (request.blocksFile.isNullOrBlank()) null else Path.of(request.blocksFile),
                    request.overwrite,
                    request.fromHeight,
                    request.upToHeight,
            )
            responseObserver.onNext(ExportBlockchainReply.newBuilder()
                    .setFromHeight(exportResult.fromHeight)
                    .setUpHeight(exportResult.toHeight)
                    .setNumBlocks(exportResult.numBlocks)
                    .build())
            responseObserver.onCompleted()
        } catch (e: UserMistake) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription(e.message).asRuntimeException()
            )
        }
    }

    override fun exportBlock(
            request: ExportBlockRequest,
            responseObserver: StreamObserver<ExportBlockReply>
    ) {
        try {
            val response = postchainService.exportBlock(request.chainId, request.height)
            responseObserver.onNext(ExportBlockReply.newBuilder()
                    .setBlockData(ByteString.copyFrom(GtvEncoder.encodeGtv(response)))
                    .build()
            )
            responseObserver.onCompleted()
        } catch (e: NotFound) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.message).asRuntimeException())
        } catch (e: Exception) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription(e.message).asRuntimeException()
            )
        }
    }

    override fun importBlockchain(request: ImportBlockchainRequest, responseObserver: StreamObserver<ImportBlockchainReply>) {
        try {
            val importResult = postchainService.importBlockchain(
                    request.chainId,
                    request.blockchainRid.toByteArray(),
                    Path.of(request.configurationsFile),
                    Path.of(request.blocksFile),
                    request.incremental
            )
            responseObserver.onNext(ImportBlockchainReply.newBuilder()
                    .setFromHeight(importResult.fromHeight)
                    .setToHeight(importResult.toHeight)
                    .setNumBlocks(importResult.numBlocks)
                    .setBlockchainRid(ByteString.copyFrom(importResult.blockchainRid.data))
                    .build())
            responseObserver.onCompleted()
        } catch (e: NotFound) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.message).asRuntimeException())
        } catch (e: Exception) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription(e.message).asRuntimeException()
            )
        }
    }

    override fun importBlock(request: ImportBlockRequest, responseObserver: StreamObserver<ImportBlockReply>) {
        try {
            val height = postchainService.importBlock(request.chainId, request.blockData.toByteArray())
            responseObserver.onNext(ImportBlockReply.newBuilder()
                    .setMessage("OK")
                    .setHeight(height)
                    .build())
            responseObserver.onCompleted()
        } catch (e: NotFound) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.message).asRuntimeException())
        } catch (e: Exception) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription(e.message).asRuntimeException()
            )
        }
    }

    override fun removeBlockchain(request: RemoveBlockchainRequest, responseObserver: StreamObserver<RemoveBlockchainReply>) {
        try {
            postchainService.removeBlockchain(request.chainId)
            responseObserver.onNext(RemoveBlockchainReply.newBuilder()
                    .setMessage("Blockchain has been removed")
                    .build()
            )
            responseObserver.onCompleted()
        } catch (e: NotFound) {
            responseObserver.onError(
                    Status.NOT_FOUND.withDescription(e.message).asRuntimeException()
            )
        } catch (e: UserMistake) {
            responseObserver.onError(
                    Status.FAILED_PRECONDITION.withDescription(e.message).asRuntimeException()
            )
        } catch (e: Exception) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription(e.message).asRuntimeException()
            )
        }
    }
}
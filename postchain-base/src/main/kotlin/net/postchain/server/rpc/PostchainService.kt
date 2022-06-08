package net.postchain.server.rpc

import io.grpc.Status
import io.grpc.stub.StreamObserver
import net.postchain.PostchainNode
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DependenciesValidator
import net.postchain.base.gtv.GtvToBlockchainRidFactory
import net.postchain.base.withWriteConnection
import net.postchain.common.hexStringToByteArray
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
        if (!request!!.hasGtv() && !request.hasXml()) {
            return responseObserver?.onError(Status.INVALID_ARGUMENT.withDescription("Both xml and gtv fields are empty").asRuntimeException())!!
        }
        withWriteConnection(postchainNode.postchainContext.storage, request.chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            val hasConfig = db.getConfigurationData(ctx, request.height) != null
            if (hasConfig && !request.override) {
                responseObserver?.onError(Status.ALREADY_EXISTS.withDescription("Configuration already exists for height ${request.height} on chain ${request.chainId}").asRuntimeException())
                return@withWriteConnection false
            }

            val gtvConfiguration = if (request.hasXml()) GtvMLParser.parseGtvML(request.xml) else GtvDecoder.decodeGtv(request.gtv.toByteArray()) // Verify that gtv-format works
            db.addConfigurationData(ctx, request.height, GtvEncoder.encodeGtv(gtvConfiguration))
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
        if (!request!!.hasGtv() && !request.hasXml()) {
            return responseObserver?.onError(Status.INVALID_ARGUMENT.withDescription("Both xml and gtv fields are empty").asRuntimeException())!!
        }
        withWriteConnection(postchainNode.postchainContext.storage, request.chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            if (db.getBlockchainRid(ctx) != null && !request.override) {
                responseObserver?.onError(Status.ALREADY_EXISTS.withDescription("Blockchain already exists").asRuntimeException())
                return@withWriteConnection false
            }


            val gtvConfiguration = if (request.hasXml()) GtvMLParser.parseGtvML(request.xml) else GtvDecoder.decodeGtv(request.gtv.toByteArray()) // Verify that gtv-format works
            val brid = GtvToBlockchainRidFactory.calculateBlockchainRid(gtvConfiguration)
            db.initializeBlockchain(ctx, brid)
            DependenciesValidator.validateBlockchainRids(ctx, listOf())
            // TODO: Blockchain dependencies [DependenciesValidator#validateBlockchainRids]
            db.addConfigurationData(ctx, 0, GtvEncoder.encodeGtv(gtvConfiguration))
            true
        }
        postchainNode.startBlockchain(request.chainId)
        responseObserver?.onNext(InitializeBlockchainReply.newBuilder().run {
            message = "Blockchain has been initialized"
            build()
        })
        responseObserver?.onCompleted()
    }

    override fun addPeer(request: AddPeerRequest?, responseObserver: StreamObserver<AddPeerReply>?) {
        val pubkey = request!!.pubkey
        if (pubkey.length != 66) {
            return responseObserver?.onError(Status.INVALID_ARGUMENT.withDescription("Public key $pubkey must be of length 66, but was ${pubkey.length}").asRuntimeException())!!
        }
        try {
            pubkey.hexStringToByteArray()
        } catch (e: java.lang.Exception) {
            return responseObserver?.onError(Status.INVALID_ARGUMENT.withDescription("Public key $pubkey must be a valid hex string").asRuntimeException())!!
        }
        postchainNode.postchainContext.storage.withWriteConnection { ctx ->
            val db = DatabaseAccess.of(ctx)
            val targetHost = db.findPeerInfo(ctx, request.host, request.port, null)
            if (targetHost.isNotEmpty()) {
                return@withWriteConnection responseObserver?.onError(Status.ALREADY_EXISTS.withDescription("Peer already exists on current host").asRuntimeException())
            }
            val targetKey = db.findPeerInfo(ctx, null, null, pubkey)
            if (targetKey.isNotEmpty() && !request.override) {
                return@withWriteConnection responseObserver?.onError(Status.ALREADY_EXISTS.withDescription("public key already added for a host, use override to add anyway").asRuntimeException())
            }
            db.addPeerInfo(ctx, request.host, request.port, pubkey)
            responseObserver?.onNext(
                AddPeerReply.newBuilder()
                    .setMessage("Peer was added successfully")
                    .build()
            )
            responseObserver?.onCompleted()
        }
    }
}
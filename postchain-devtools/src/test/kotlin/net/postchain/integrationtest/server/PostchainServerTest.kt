package net.postchain.integrationtest.server

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.grpc.StatusRuntimeException
import net.postchain.common.BlockchainRid
import net.postchain.devtools.getModules
import net.postchain.integrationtest.reconfiguration.DummyModule1
import net.postchain.integrationtest.reconfiguration.DummyModule2
import net.postchain.server.grpc.AddConfigurationRequest
import net.postchain.server.grpc.ExportBlockchainRequest
import net.postchain.server.grpc.FindBlockchainRequest
import net.postchain.server.grpc.ImportBlockchainRequest
import net.postchain.server.grpc.InitializeBlockchainRequest
import net.postchain.server.grpc.ListConfigurationsRequest
import net.postchain.server.grpc.PeerServiceGrpc
import net.postchain.server.grpc.PostchainServiceGrpc
import net.postchain.server.grpc.RemoveBlockchainRequest
import net.postchain.server.grpc.StartBlockchainRequest
import net.postchain.server.grpc.StopBlockchainRequest
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.io.path.createTempFile

class PostchainServerTest : PostchainServerBase() {

    private lateinit var postchainServiceStub: PostchainServiceGrpc.PostchainServiceBlockingStub
    private lateinit var peerServiceStub: PeerServiceGrpc.PeerServiceBlockingStub

    @BeforeEach
    fun setup() {
        setupNode()
        postchainServiceStub = setupPostchainService(nodeProvider)
        peerServiceStub = setupPeerService()
    }

    @Test
    fun `Stop and start blockchain via gRPC`() {
        // Ensure we can find the default test chain
        val actualBrid = node.getBlockchainInstance(1L).blockchainEngine.getConfiguration().blockchainRid
        val findBlockchainReply = postchainServiceStub.findBlockchain(
                FindBlockchainRequest.newBuilder()
                        .setChainId(1L)
                        .build()
        )
        assertThat(findBlockchainReply.brid).isEqualTo(actualBrid.toHex())

        // Stop
        postchainServiceStub.stopBlockchain(
                StopBlockchainRequest.newBuilder()
                        .setChainId(1L)
                        .build()
        )
        assertThat(node.retrieveBlockchain(1L)).isNull()

        // Start again
        postchainServiceStub.startBlockchain(
                StartBlockchainRequest.newBuilder()
                        .setChainId(1L)
                        .build()
        )
        assertThat(node.retrieveBlockchain(1L)).isNotNull()
    }

    @Test
    fun `Add configuration`() {
        assertThat(node.getModules(1L)[0]).isInstanceOf(DummyModule1::class)

        var listConfigurationsReply = postchainServiceStub.listConfigurations(ListConfigurationsRequest.newBuilder().setChainId(1L).build())
        assertThat(listConfigurationsReply.heightCount).isEqualTo(1)

        val configXml = javaClass.getResource("/net/postchain/devtools/server/blockchain_config_2.xml")!!.readText()
        postchainServiceStub.addConfiguration(
                AddConfigurationRequest.newBuilder()
                        .setChainId(1L)
                        .setHeight(1L)
                        .setXml(configXml)
                        .build()
        )

        // Build a block so config is applied
        buildBlock(0)

        // Await restart
        Awaitility.await().atMost(Duration.TEN_SECONDS).untilAsserted {
            assertThat(node.getModules()).isNotEmpty()
            assertThat(node.getModules(1L)[0]).isInstanceOf(DummyModule2::class)
        }

        listConfigurationsReply = postchainServiceStub.listConfigurations(ListConfigurationsRequest.newBuilder().setChainId(1L).build())
        assertThat(listConfigurationsReply.heightCount).isEqualTo(2)
        assertThat(listConfigurationsReply.getHeight(0)).isEqualTo(0L)
        assertThat(listConfigurationsReply.getHeight(1)).isEqualTo(1L)
    }

    @Test
    fun `Initialize blockchain via configuration`() {
        val configXml = javaClass.getResource("/net/postchain/devtools/server/blockchain_config_2.xml")!!.readText()
        postchainServiceStub.initializeBlockchain(InitializeBlockchainRequest.newBuilder()
                .setChainId(2L)
                .setOverride(false)
                .setXml(configXml).build())

        val actualBrid = node.getBlockchainInstance(2L).blockchainEngine.getConfiguration().blockchainRid
        val findBlockchainReply = postchainServiceStub.findBlockchain(
                FindBlockchainRequest.newBuilder()
                        .setChainId(2L)
                        .build()
        )
        assertThat(findBlockchainReply.brid).isEqualTo(actualBrid.toHex())
    }

    @Test
    fun `Export and Import blockchain`() {
        buildNonEmptyBlocks(-1, 9)

        val confFilePath = createTempFile("configuration")
        confFilePath.toFile().deleteOnExit()
        val blockFilePath = createTempFile("blocks")
        blockFilePath.toFile().deleteOnExit()
        val actualBrid = node.getBlockchainInstance(1L).blockchainEngine.getConfiguration().blockchainRid

        println(confFilePath.toString())
        println(blockFilePath.toString())

        val exportBlockchainReply = postchainServiceStub.exportBlockchain(
                ExportBlockchainRequest.newBuilder()
                        .setChainId(1L)
                        .setConfigurationsFile(confFilePath.toString())
                        .setBlocksFile(blockFilePath.toString())
                        .setFromHeight(0)
                        .setUpToHeight(Long.MAX_VALUE)
                        .setOverwrite(true)
                        .build())

        assertThat(exportBlockchainReply.fromHeight).isEqualTo(0)
        assertThat(exportBlockchainReply.upHeight).isEqualTo(9)
        assertThat(exportBlockchainReply.numBlocks).isEqualTo(10)

        postchainServiceStub.removeBlockchain(RemoveBlockchainRequest.newBuilder().setChainId(1L).build())

        val importBlockchainReply = postchainServiceStub.importBlockchain(
                ImportBlockchainRequest.newBuilder()
                        .setChainId(2L)
                        .setConfigurationsFile(confFilePath.toString())
                        .setBlocksFile(blockFilePath.toString())
                        .setIncremental(false)
                        .build()
        )
        assertThat(importBlockchainReply.fromHeight).isEqualTo(0)
        assertThat(importBlockchainReply.toHeight).isEqualTo(9)
        assertThat(importBlockchainReply.numBlocks).isEqualTo(10)
        assertThat(BlockchainRid(importBlockchainReply.blockchainRid.toByteArray())).isEqualTo(actualBrid)
    }

    @Test
    fun `Remove blockchain`() {
        val removeBlockchainReply = postchainServiceStub.removeBlockchain(RemoveBlockchainRequest.newBuilder().setChainId(1L).build())
        assertThat(removeBlockchainReply.message).isEqualTo("Blockchain has been removed")
        assertThrows<StatusRuntimeException> { postchainServiceStub.removeBlockchain(RemoveBlockchainRequest.newBuilder().setChainId(1L).build()) }
    }
}
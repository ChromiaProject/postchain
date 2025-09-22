package net.postchain.containers.bpm

import assertk.assertThat
import assertk.assertions.isTrue
import com.github.dockerjava.api.command.LogContainerCmd
import mu.KLogging
import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.command.DefaultCommandExecutor
import net.postchain.containers.bpm.docker.DockerTools.asyncExecAwaitMultiResponse
import net.postchain.containers.bpm.fs.LocalFileSystem
import net.postchain.containers.bpm.rpc.DefaultSubnodeAdminClient
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.containers.infra.ContainerNodeConfig.Companion.KEY_HOST_MOUNT_DIR
import net.postchain.containers.infra.ContainerNodeConfig.Companion.KEY_MASTER_HOST
import net.postchain.containers.infra.ContainerNodeConfig.Companion.KEY_SUBNODE_HOST
import net.postchain.containers.infra.ContainerNodeConfig.Companion.fullKey
import net.postchain.crypto.PrivKey
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.gtv.GtvDictionary
import org.awaitility.Awaitility.await
import org.awaitility.Duration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.net.InetAddress
import java.net.URI
import java.nio.file.Path


internal class ContainerHandlerIT {
    companion object : KLogging()

    private lateinit var sut: ContainerHandler
    private var containerId: String? = null
    private val postchainContainerMock = mock<PostchainContainer> {
        on { containerId } doAnswer { containerId }
    }

    @Test
    @Tag("docker")
    fun `create and start container`(@TempDir tempDir: Path) {
        val dockerHost = getResolvedDockerHost()?.host ?: System.getProperty("DOCKER_HOST_MASTER", "172.17.0.1")
        val appConfig = AppConfig.fromPropertiesFile(
                File(javaClass.getResource("/net/postchain/containers/bpm/job/node.properties")!!.toURI()),
                mapOf(
                        fullKey(KEY_MASTER_HOST) to dockerHost,
                        "metrics.sub_container_resource_interval_ms" to -1,
                        "metrics.sub_container_space_resource_interval_ms" to -1,
                        fullKey(KEY_SUBNODE_HOST) to System.getProperty("DOCKER_HOST_SUBNODE", dockerHost),
                        fullKey(KEY_HOST_MOUNT_DIR) to (System.getenv("TEST_MOUNT_DIRECTORY")
                                ?: tempDir.toAbsolutePath().toString()),
                )
        )
        val containerNodeConfig = ContainerNodeConfig.fromAppConfig(appConfig)
        ContainerEnvironment.init(appConfig)
        val fileSystem = LocalFileSystem(containerNodeConfig, DefaultCommandExecutor)
        whenever(postchainContainerMock.containerName)
                .doReturn(ContainerName.create(appConfig.pubKey, "docker-container-name", 20))

        sut = ContainerHandler(ContainerEnvironment.dockerClient, appConfig, fileSystem)

        sut.pullImage(containerNodeConfig.containerImage)
        val containerName = ContainerName.create(appConfig.pubKey, "the_container", 1)
        val resourceLimits = ContainerResourceLimits.default()
        fileSystem.createContainerRoot(containerName, resourceLimits)
        containerId = sut.createDockerContainer(
                containerName,
                resourceLimits,
                false,
                containerNodeConfig.containerImage,
                GtvDictionary.build(emptyMap())
        )
        logger.debug { ContainerEnvironment.dockerClient.inspectContainerCmd(containerId!!).exec().toString() }
        sut.startContainer(postchainContainerMock)
        await().atMost(Duration.TEN_SECONDS).untilAsserted {
            assertThat(ContainerEnvironment.dockerClient.inspectContainerCmd(containerId!!).exec().state.running!!).isTrue()
        }
        val containerPortMapping = sut.findHostPorts(containerId!!, containerNodeConfig.subnodePorts)
        val nodeDiagnosticContext: NodeDiagnosticContext = mock()
        val subnodeAdminClient = DefaultSubnodeAdminClient(containerName, containerNodeConfig, containerPortMapping, nodeDiagnosticContext)
        subnodeAdminClient.connectBlocking()
        await().atMost(Duration.TEN_SECONDS).untilAsserted {
            assertThat(subnodeAdminClient.initializePostchainNode(PrivKey(appConfig.privKeyByteArray))).isTrue()
        }
        subnodeAdminClient.disconnect()
    }

    @AfterEach
    fun tearDown() {
        containerId?.let {
            logger.info("Stopping container $it...")
            sut.stopContainer(postchainContainerMock)
            logger.info("Collecting logs from container $it...")

            val logContainerCmd: LogContainerCmd = ContainerEnvironment.dockerClient.logContainerCmd(it)
                    .withStdOut(true)
                    .withStdErr(true)

            logContainerCmd.asyncExecAwaitMultiResponse({ logEntry ->
                logger.info("[Subnode] " + String(logEntry.payload).trim())
            }, { throwable ->
                logger.error("[Subnode] Failed to collect logs", throwable)
            })

            logger.info("Removing container $it...")
            ContainerEnvironment.dockerClient.killContainerCmd(it)
        }
    }

    private fun getResolvedDockerHost(): URI? {
        return if (System.getenv("DOCKER_HOST") != null) {
            val dockerUri = URI(System.getenv("DOCKER_HOST"))
            // Pass docker host to master container with hostname resolved
            URI("${dockerUri.scheme}://${InetAddress.getByName(dockerUri.host).hostAddress}:${dockerUri.port}")
        } else {
            null
        }
    }
}

package net.postchain.containers.bpm.job

import net.postchain.config.app.AppConfig
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.containers.bpm.ContainerName
import net.postchain.containers.bpm.PostchainContainer
import org.junit.jupiter.api.Test
import org.mandas.docker.client.DockerClient
import org.mandas.docker.client.messages.Container
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.`when`
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class ContainerHealthcheckHandlerTest {

    companion object {
        private const val PUBKEY = "12345678"
        private const val NODE_NAME = "NODE"
        private const val CONTAINER_IID = 42
        private const val CHAIN_ID = 54L
    }

    private val appConfig: AppConfig = mock {
        on { pubKey } doReturn PUBKEY
    }
    private val postchainContainer: PostchainContainer = mock()
    private val dockerClient: DockerClient = mock()
    private val psContainers = mutableMapOf<ContainerName, PostchainContainer>()
    private val cname = ContainerName.create(appConfig, "", CONTAINER_IID)
    private val postchainContainers = { psContainers }
    private var removedBlockchainProcess: Pair<Long, PostchainContainer>? = null
    private var chainsRemoved = 0
    private val removeBlockchainProcess: (Long, PostchainContainer) -> ContainerBlockchainProcess? = { id, psContainer ->
        removedBlockchainProcess = id to psContainer
        chainsRemoved++
        null
    }
    private lateinit var sut: ContainerHealthcheckHandler

    @BeforeTest
    fun beforeTest() {
        chainsRemoved = 0
        psContainers.clear()
        psContainers[cname] = postchainContainer
        sut = ContainerHealthcheckHandler(appConfig, NODE_NAME, dockerClient, postchainContainers, removeBlockchainProcess)
    }

    @Test
    fun `container in progress should not be checked`() {
        // execute
        sut.check(setOf(cname.name))
        // verify
        verify(dockerClient, never()).listContainers()
    }

    @Test
    fun `container with updated resource limits should be restarted`() {
        // setup
        `when`(postchainContainer.updateResourceLimits()).thenReturn(true)
        `when`(postchainContainer.getAllChains()).thenReturn(setOf(CHAIN_ID))
        mockContainerIsRunning()
        // execute
        sut.check(emptySet())
        // verify
        verify(postchainContainer).reset()
        verify(dockerClient).stopContainer(anyString(), anyInt())
        verify(dockerClient).removeContainer(anyString())
        assertEquals(removedBlockchainProcess!!.first, CHAIN_ID)
        assertEquals(removedBlockchainProcess!!.second, postchainContainer)
    }

    @Test
    fun `not running subnode container should be restarted`() {
        // setup
        `when`(postchainContainer.getAllChains()).thenReturn(setOf(CHAIN_ID))
        `when`(dockerClient.listContainers()).thenReturn(emptyList())
        // execute
        sut.check(emptySet())
        // verify
        assertEquals(removedBlockchainProcess!!.first, CHAIN_ID)
        assertEquals(removedBlockchainProcess!!.second, postchainContainer)
    }

    @Test
    fun `unhealthy subnode should restart container`() {
        // setup
        `when`(postchainContainer.isSubnodeHealthy()).thenReturn(false)
        `when`(postchainContainer.getAllChains()).thenReturn(setOf(CHAIN_ID))
        mockContainerIsRunning()
        // execute
        sut.check(emptySet())
        // verify
        verify(dockerClient).stopContainer(anyString(), anyInt())
        assertEquals(removedBlockchainProcess!!.first, CHAIN_ID)
        assertEquals(removedBlockchainProcess!!.second, postchainContainer)
    }

    @Test
    fun `healthy should stop stopped chains`() {
        // setup
        `when`(postchainContainer.isSubnodeHealthy()).thenReturn(true)
        `when`(postchainContainer.getAllChains()).thenReturn(setOf(CHAIN_ID, 123))
        `when`(postchainContainer.getStoppedChains()).thenReturn(setOf(CHAIN_ID))
        mockContainerIsRunning()
        // execute
        sut.check(emptySet())
        // verify
        assertEquals(removedBlockchainProcess!!.first, CHAIN_ID)
        assertEquals(removedBlockchainProcess!!.second, postchainContainer)
        assertEquals(chainsRemoved, 1)
    }

    private fun mockContainerIsRunning() {
        val container: Container = mock()
        doReturn(listOf("/${cname.name}")).`when`(container).names()
        `when`(dockerClient.listContainers()).thenReturn(listOf(container))
    }
}
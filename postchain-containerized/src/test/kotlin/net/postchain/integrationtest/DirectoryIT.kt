package net.postchain.integrationtest

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.DockerClient.LogsParam
import net.postchain.base.PeerInfo
import net.postchain.containers.bpm.DockerClientFactory
import net.postchain.core.BlockchainRid
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.MockManagedNodeDataSource
import net.postchain.devtools.chainRidOf
import org.apache.commons.configuration2.Configuration
import org.junit.Before
import org.junit.Test
import java.lang.Thread.sleep
import kotlin.test.assertEquals
import kotlin.test.assertTrue

const val commonContainerName = "postchainCont"
const val firstContainerName = commonContainerName + "1"
const val secondContainerName = commonContainerName + "2"
val blockchainDistribution: Map<String, List<BlockchainRid>> = mapOf(
        firstContainerName to listOf(chainRidOf(1)),
        secondContainerName to listOf(chainRidOf(2), chainRidOf(3))
)

/**
 * For the tests below, a docker image is needed. It can be build with e.g:  `mvn (clean) verify -Dskip.surefire.tests`
 * Bitbucket builds image on the fly.
 * Please note: Currently for the tests to pass, flag waitForRestart must be set.
 */
class DirectoryIT : ManagedModeTest() {

    override val awaitDebugLog = false
    private val dockerClient: DockerClient = DockerClientFactory.create()

    @Before
    fun setUp() {
        // If container UUTs already exist, remove them
        val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
        all.forEach {
            if (it.names()?.get(0)?.contains(Regex(commonContainerName))!!) {
                println("removing existing container: " + it.names())
                dockerClient.stopContainer(it.id(), 0)
                dockerClient.removeContainer(it.id())
            }
        }
    }

    /**
     * Why await height > 0? Technically 0 is not enough, because subnode builds block0 with its own initial config for height 0 then it looks
     * for the remote config. So 1 might be, but 1 is the corner-/edge-case for the test, so I would use something > 1.
     */
    @Test
    fun testSingleChain() {
        startManagedSystem(1, 0)
        val c1 = startNewBlockchain(setOf(0), setOf(), waitForRestart = true) //location defined in blockchainDistribution
        awaitHeight(c1.chain, 2)
    }

    /**
     * Directory with one signer, no replicas. Signer is signer of all three chains. c0 is run on master node and c1
     * is run in container "cont1" by the subnode. c2, and c3 in cont2
     */
    @Test
    fun testMultipleChains() {
        startManagedSystem(1, 0)
        val c1 = startNewBlockchain(setOf(0), setOf(), waitForRestart = true)
        val c2 = startNewBlockchain(setOf(0), setOf(), waitForRestart = true)  //We must wait until chain2 is up and running (waitForRestart = true) before we start chain3 in the same container.
        val c3 = startNewBlockchain(setOf(0), setOf(), waitForRestart = true)  //location defined in blockchainDistribution
        awaitHeight(c1.chain, 0)
        awaitHeight(c2.chain, 0)
        awaitHeight(c3.chain, 0)
    }

    /**
     * With more than one node, docker port, container name and directory for files must be node specific.
     */
    @Test
    fun testMultipleNodes() {
        startManagedSystem(2, 0)
        val c1 = startNewBlockchain(setOf(0, 1), setOf(), waitForRestart = true)
        awaitHeight(c1.chain, 0)
    }

    /**
     * Assert that ram and cpu limits can be set on the container
     */
    @Test
    fun testContainerResourceLimits() {
        startManagedSystem(1, 0)
        buildBlock(c0, 0)
        val ramLimit = 6_000_000_000L
        val cpuQuotaLimit = 90_000L

        //update dataSource with limit value. This is used when container is created (getResourceLimitForContainer)
        dataSource(0).setLimitsForContainer(firstContainerName, ramLimit, cpuQuotaLimit)

        startNewBlockchain(setOf(0), setOf(), waitForRestart = true)
        val dockerClient: DockerClient = DefaultDockerClient.fromEnv().build()
        val listc = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
        listc.forEach {
            if (it.names()?.get(0)?.contains(Regex(firstContainerName))!!) {
                val res = dockerClient.inspectContainer(it.id())
                println("checking resource limits")
                assertEquals(ramLimit, res.hostConfig()?.memory())
                assertEquals(cpuQuotaLimit, res.hostConfig()?.cpuQuota())
            }
        }
    }

    private fun dataSource(nodeIndex: Int): MockDirectoryDataSource {
        return mockDataSources[nodeIndex] as MockDirectoryDataSource
    }

    override fun createMockDataSource(nodeIndex: Int): MockManagedNodeDataSource {
        return MockDirectoryDataSource(nodeIndex)
    }

    override fun awaitChainRunning(index: Int, chainId: Long, atLeastHeight: Long) {
        val pm = nodes[index].processManager as TestContainerManagedBlockchainProcessManager
        // await subnode ready for heartbeat:
        val sleepTime = 1_000L
        while (dataSource(index).subnodeInterceptors[chainRidOf(chainId)]?.subnodeStatus == null) {
            buildBlock(c0)
            sleep(sleepTime)
        }
        // need to continue building blocks on c0 (heartbeat) until subnode has started:
        while (dataSource(index).subnodeInterceptors[chainRidOf(chainId)]?.subnodeStatus == -2L) {
            buildBlock(c0)
            sleep(sleepTime)
        }
        //await a specific (configuration height-1)
        pm.awaitStarted(index, chainId, atLeastHeight)
    }

    override fun awaitHeight(chainId: Long, height: Long) {
        val sleepTime = 1000L // 1000ms is not so much for Docker tests
        awaitLog("========= AWAIT ALL ${nodes.size} NODES chain:  $chainId, height:  $height (i)")

        val start = System.currentTimeMillis()
        var running: Boolean
        do {
            sleep(sleepTime)
            running = nodes.indices.any {
                val subnode = dataSource(it).subnodeInterceptors[chainRidOf(chainId)]
                val res = subnode == null || subnode.subnodeStatus < height
                if (res) {
                    awaitLog("ChainId: $chainId, node idx: $it, subnode height: ${subnode?.subnodeStatus}")
                }
                res
            }

            /* // NB: Don't delete. Uncomment to get container node logs
            println("---------- Cont1 logs ----------")
            println(getCont1Logs())
            println("---------- END of Cont1 logs ----------")
             */

            // Stop after 5 min
            if ((System.currentTimeMillis() - start) / 60_000 > 5) {
                running = false
                assertTrue(false, "awaitHeight: timeout occurred")
            }

        } while (running)

        awaitLog("========= DONE AWAIT ALL ${nodes.size} NODES chain: $chainId, height: $height (i)")
    }

    override fun nodeConfigurationMap(nodeIndex: Int, peerInfo: PeerInfo): Configuration {
        val propertyMap = super.nodeConfigurationMap(nodeIndex, peerInfo)
        val className = TestDirectoryMasterInfraFactory::class.qualifiedName
        val masterHost = System.getenv("POSTCHAIN_TEST_MASTER_HOST") ?: "172.17.0.1" // Default Docker host
        val dbHost = System.getenv("POSTCHAIN_TEST_DB_HOST") ?: "localhost"
        propertyMap.setProperty("infrastructure", className)
        propertyMap.setProperty("containerChains.masterPort", 9860 - nodeIndex)
        propertyMap.setProperty("containerChains.masterHost", masterHost)
        propertyMap.setProperty("configDir", System.getProperty("user.dir"))
        propertyMap.setProperty("subnode.database.url", "jdbc:postgresql://$dbHost:5432/postchain")
        propertyMap.setProperty("brid.chainid.1", chainRidOf(1).toHex())
        propertyMap.setProperty("brid.chainid.2", chainRidOf(2).toHex())
        propertyMap.setProperty("brid.chainid.3", chainRidOf(3).toHex())
        propertyMap.setProperty("heartbeat.enabled", true)
        propertyMap.setProperty("heartbeat.timeout", 6_000_000L) //default 60_000
        propertyMap.setProperty("heartbeat.sleep_timeout", 500L) //default 5_000
        propertyMap.setProperty("remote_config.enabled", false)
        return propertyMap
    }

    /**
     * Returns 50 tail lines of log of Cont1 container.
     * NB: Don't delete this function. It's useful for the diagnosis of the container's node.
     * TODO: Extract cont1 to arg
     */
    private fun getCont1Logs(): String {
        val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
        val cont1 = all.find { it.names()?.get(0)?.startsWith("/$firstContainerName") ?: false }
        return if (cont1 != null) {
            dockerClient.logs(cont1.id(), LogsParam.stdout(), LogsParam.tail(50))
                    .readFully()
        } else "<no cont1 logs>"
    }
}

package net.postchain.integrationtest

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient
import net.postchain.base.BlockchainRid
import net.postchain.base.PeerInfo
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadWriteConnection
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfig
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.containers.api.DefaultMasterApiInfra
import net.postchain.containers.api.MasterApiInfra
import net.postchain.containers.bpm.ContainerManagedBlockchainProcessManager
import net.postchain.containers.infra.*
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.core.EContext
import net.postchain.debug.BlockTrace
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.devtools.*
import net.postchain.gtv.GtvFactory
import net.postchain.managed.DirectoryDataSource
import net.postchain.managed.ManagedNodeDataSource
import org.apache.commons.configuration2.Configuration
import org.junit.Ignore
import org.junit.Test
import java.lang.Thread.sleep
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.assertEquals

const val firstContainerName = "cont1" //for chain 1, 2
const val secondContainerName = "cont3" //for chain 3

/**
 * For the tests below, a docker image is needed. It can be build with e.g:  `mvn (clean) verify -Dskip.surefire.tests`
 *
 */
class DirectoryIT : ManagedModeTest() {

    /**
     * Directory with one signer, no replicas. Signer is signer of all three chains. c0 is run on master node and c1, c2
     * is run in container "cont1" by the subnode.
     *
     */
    @Test
    fun testMultipleChains() {
        startManagedSystem(1, 0)
        buildBlock(c0, 0)
        val c1 = startNewBlockchain(setOf(0), setOf(), waitForRestart = false)
//        val c2 = startNewBlockchain(setOf(0), setOf(), waitForRestart = false)
//        val c3 = startNewBlockchain(setOf(0), setOf(), waitForRestart = false)  //c3 in cont3
        //TODO: waitForRestart does not work since we do not have access to heights of chains run o0n subnodes.
        // Instead, whait (sleep) before tear-down to see chains are started in the container:
        sleep(20_000L)
    }


    /**
     * With more than one node, docker port, container name and directory for files must be node specific.
     */
    @Ignore
    @Test
    fun testMultipleNodes() {
        startManagedSystem(2, 0)
        buildBlock(c0, 0)
        val c1 = startNewBlockchain(setOf(0, 1), setOf(), waitForRestart = false)
        //TODO: waitForRestart does not work since we do not have access to heights of chains run on subnodes.
        // Instead, whait with tear-down to see chains are started in the container:
        sleep(20_000)
    }

    /**
     * Assert that ram and cpu limits can be set on the container
     */
    @Ignore
    @Test
    fun testResourceLimits() {
        val dockerClient: DockerClient = DefaultDockerClient.fromEnv().build()
        var listc = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
        listc.forEach {
            if (it.names()?.get(0)?.contains(Regex(firstContainerName))!!) {
                println("removing existing container: " + it.names())
                dockerClient.stopContainer(it.id(), 0)
                dockerClient.removeContainer(it.id())
            }
        }
        startManagedSystem(1, 0)
        buildBlock(c0, 0)
        val ramLimit = 7000_000L
        val cpuQuotaLimit = 90_000L
        //update dataSource with limit value. This is used when container is created (getResourceLimitForContainer)
        dataSource(0).setLimitsForContainer(firstContainerName, ramLimit, cpuQuotaLimit)
        startNewBlockchain(setOf(0), setOf(), waitForRestart = false)
        sleep(20_000) //we must wait a bit to ensure that container has been created.
        listc = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
        println("number of containers: " + listc.size)
        val res = dockerClient.inspectContainer(listc[0].id())
        assertEquals(ramLimit, res.hostConfig()?.memory())
        assertEquals(cpuQuotaLimit, res.hostConfig()?.cpuQuota())
    }

    private fun dataSource(nodeIndex: Int): MockDirectoryDataSource {
        return mockDataSources[nodeIndex] as MockDirectoryDataSource
    }

//    System providers create a ‘system’ cluster which includes ‘system’ nodes and has a ‘system’ naked container which will run the directory.
    /**
     * MockDirectoryDataSource is populated with two containers and n0, n1 are block builders for both no replicas.
     * startManagedSystem() starts bc0 in system container.
     * startNewBlockchain() should start bc1 in cont1 container
     */
//    fun startDirectory() {
//        //Create system cluster with system nodes n0, n1
//        //Create naked system container
//        //start bc0 in system container: n0, n1 build blocks, no replicas
//        startManagedSystem(1, 0)
//    }

    override fun createMockDataSource(nodeIndex: Int): MockManagedNodeDataSource {
        return MockDirectoryDataSource(nodeIndex)
    }

    override fun awaitChainRunning(index: Int, chainId: Long, atLeastHeight: Long) {
        val pm = nodes[index].processManager as TestContainerManagedBlockchainProcessManager
        pm.awaitStarted(chainId, atLeastHeight)
    }

    override fun nodeConfigurationMap(nodeIndex: Int, peerInfo: PeerInfo): Configuration {
        val propertyMap = super.nodeConfigurationMap(nodeIndex, peerInfo)
        var className = TestDirectoryMasterInfraFactory::class.qualifiedName
        propertyMap.setProperty("infrastructure", className)
        propertyMap.setProperty("containerChains.masterPort", 9860 - nodeIndex)
        propertyMap.setProperty("containerChains.masterHost", "172.17.0.1")
        propertyMap.setProperty("configDir", System.getProperty("user.dir"))
        propertyMap.setProperty("subnode.database.url", "jdbc:postgresql://localhost:5432/postchain")
        propertyMap.setProperty("brid.chainid.1", chainRidOf(1).toHex())
        propertyMap.setProperty("brid.chainid.2", chainRidOf(2).toHex())
        propertyMap.setProperty("brid.chainid.3", chainRidOf(3).toHex())
        return propertyMap
    }
}

class TestDirectoryMasterInfraFactory : MasterManagedEbftInfraFactory() {
    lateinit var nodeConfig: NodeConfig
    lateinit var dataSource: MockDirectoryDataSource

    override fun makeProcessManager(nodeConfigProvider: NodeConfigurationProvider,
                                    blockchainInfrastructure: BlockchainInfrastructure,
                                    blockchainConfigurationProvider: BlockchainConfigurationProvider,
                                    nodeDiagnosticContext: NodeDiagnosticContext): BlockchainProcessManager {
        return TestContainerManagedBlockchainProcessManager(blockchainInfrastructure as MasterBlockchainInfra,
                nodeConfigProvider,
                blockchainConfigurationProvider, nodeDiagnosticContext, dataSource)
    }

    override fun makeBlockchainInfrastructure(nodeConfigProvider: NodeConfigurationProvider,
                                              nodeDiagnosticContext: NodeDiagnosticContext): BlockchainInfrastructure {
        nodeConfig = nodeConfigProvider.getConfiguration()
        dataSource = nodeConfig.appConfig.config.get(MockDirectoryDataSource::class.java, "infrastructure.datasource")!!
        val syncInfra = DefaultMasterSyncInfra(nodeConfigProvider, nodeDiagnosticContext)
        val apiInfra = DefaultMasterApiInfra(nodeConfigProvider, nodeDiagnosticContext)
        return TestMasterBlockchainInfrastructure(nodeConfigProvider, syncInfra, apiInfra, nodeDiagnosticContext, dataSource)
    }

    override fun makeBlockchainConfigurationProvider(): BlockchainConfigurationProvider {
        return TestBlockchainConfigurationProvider(dataSource)
    }
}

class TestContainerManagedBlockchainProcessManager(blockchainInfrastructure: MasterBlockchainInfra,
                                                   nodeConfigProvider: NodeConfigurationProvider,
                                                   blockchainConfigProvider: BlockchainConfigurationProvider,
                                                   nodeDiagnosticContext: NodeDiagnosticContext,
                                                   val testDataSource: ManagedNodeDataSource)
    : ContainerManagedBlockchainProcessManager(blockchainInfrastructure,
        nodeConfigProvider,
        blockchainConfigProvider,
        nodeDiagnosticContext) {

    private val blockchainStarts = ConcurrentHashMap<Long, BlockingQueue<Long>>()

    override fun buildChain0ManagedDataSource(): ManagedNodeDataSource {
        return testDataSource
    }

    override fun retrieveBlockchainsToLaunch(): Set<Long> {
        val result = mutableListOf<Long>()
        testDataSource.computeBlockchainList().forEach {
            val brid = BlockchainRid(it)
            val chainIid = chainIidOf(brid)
            result.add(chainIid)
            withReadWriteConnection(storage, chainIid) { newCtx ->
                DatabaseAccess.of(newCtx).initializeBlockchain(newCtx, brid)
            }
            val i = 0
        }
        return result.toSet()
    }

    private fun getQueue(chainId: Long): BlockingQueue<Long> {
        return blockchainStarts.computeIfAbsent(chainId) {
            LinkedBlockingQueue<Long>()
        }
    }

    var lastHeightStarted = ConcurrentHashMap<Long, Long>()

    //    val containerChainStarted = ConcurrentHashMap<Long, Boolean>()
    override fun startBlockchain(chainId: Long, bTrace: BlockTrace?): BlockchainRid? {
        val blockchainRid = super.startBlockchain(chainId, bTrace)
        if (blockchainRid == null) {
            return null
        }
        if (chainId == 0L) { //only chain0 is run on master, all other chains in containers
            val process = blockchainProcesses[chainId]!!
            val queries = process.getEngine().getBlockQueries()
            val height = queries.getBestHeight().get()
            lastHeightStarted[chainId] = height
        }
        return blockchainRid
    }

    fun awaitStarted(chainId: Long, atLeastHeight: Long) {
        while (lastHeightStarted.get(chainId) ?: -2L < atLeastHeight) {
            sleep(10)
        }
    }
}


class TestMasterBlockchainInfrastructure(nodeConfigProvider: NodeConfigurationProvider,
                                         syncInfra: MasterSyncInfra, apiInfra: MasterApiInfra,
                                         nodeDiagnosticContext: NodeDiagnosticContext, val mockDataSource: MockDirectoryDataSource) :
        DefaultMasterBlockchainInfra(nodeConfigProvider, syncInfra, apiInfra, nodeDiagnosticContext) {
    override fun makeBlockchainConfiguration(rawConfigurationData: ByteArray, eContext: EContext, nodeId: Int, chainId: Long): BlockchainConfiguration {
        return mockDataSource.getConf(rawConfigurationData)!!
    }
}

class MockDirectoryDataSource(nodeIndex: Int) : MockManagedNodeDataSource(nodeIndex), DirectoryDataSource {

    var ram = 7000_000_000L
    var cpu = 100_000L

    override fun getConfigurations(blockchainRidRaw: ByteArray): Map<Long, ByteArray> {
        val l = bridToConfs[BlockchainRid(blockchainRidRaw)] ?: return mapOf()
        var confs = mutableMapOf<Long, ByteArray>()
        for (entry in l) {
            val data = entry.value.second
            // try to decode to ensure data is valid
            val entireBcConf = GtvFactory.decodeGtv(data).asDict()
            confs.put(entry.key, data)
        }
        return confs
    }

    override fun getContainersToRun(): List<String>? {
        return listOf("system", firstContainerName, secondContainerName)
    }

    //chain 0 in system container, chain1-2 in cont1 container. chain3 in cont3 container.
    override fun getBlockchainsForContainer(containerID: String): List<BlockchainRid>? {
        if (containerID == firstContainerName) {
            return listOf(chainRidOf(1), chainRidOf(2))
        } else if (containerID == secondContainerName) {
            return listOf(chainRidOf(3))
        } else {
            return listOf(chainRidOf(0))
        }
    }

    override fun getContainerForBlockchain(brid: BlockchainRid): String {
        if ((brid == chainRidOf(1)) or (brid == chainRidOf(2))) {
            return firstContainerName
        } else if (brid == chainRidOf(3)) {
            return secondContainerName
        } else {
            return "system"
        }
    }

    override fun getResourceLimitForContainer(containerID: String): Map<ContainerResourceType, Long>? {
        if (containerID == firstContainerName) {
            return mapOf(ContainerResourceType.STORAGE to 10L, ContainerResourceType.RAM to ram,
                    ContainerResourceType.CPU to cpu)
        }
        return mapOf() //no limits for naked system container.
    }

    override fun setLimitsForContainer(containerID: String, ramLimit: Long, cpuQuota: Long) {
        if (containerID == firstContainerName) {
            ram = ramLimit
            cpu = cpuQuota
        }
    }
}
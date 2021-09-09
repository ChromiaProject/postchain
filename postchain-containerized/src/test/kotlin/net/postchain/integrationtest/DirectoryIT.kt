package net.postchain.integrationtest

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.DockerClient.LogsParam
import net.postchain.base.BlockchainRid
import net.postchain.base.PeerInfo
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadWriteConnection
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfig
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.containers.api.DefaultMasterApiInfra
import net.postchain.containers.api.MasterApiInfra
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.containers.bpm.ContainerChainDir
import net.postchain.containers.bpm.ContainerManagedBlockchainProcessManager
import net.postchain.containers.bpm.DefaultContainerBlockchainProcess
import net.postchain.containers.infra.*
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.core.EContext
import net.postchain.debug.BlockTrace
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.devtools.*
import net.postchain.ebft.message.Message
import net.postchain.ebft.message.SignedMessage
import net.postchain.ebft.message.Status
import net.postchain.gtv.GtvFactory
import net.postchain.managed.DirectoryDataSource
import net.postchain.managed.ManagedNodeDataSource
import net.postchain.network.masterslave.MsMessageHandler
import net.postchain.network.masterslave.master.DefaultMasterCommunicationManager
import net.postchain.network.masterslave.master.MasterConnectionManager
import net.postchain.network.masterslave.master.SlaveChainConfig
import net.postchain.network.masterslave.protocol.MsDataMessage
import net.postchain.network.masterslave.protocol.MsMessage
import net.postchain.network.masterslave.protocol.MsSubnodeStatusMessage
import net.postchain.network.x.PeersCommConfigFactory
import org.apache.commons.configuration2.Configuration
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.lang.Thread.sleep
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val commonContainerName = "postchainCont"
private const val firstContainerName = commonContainerName + "1"
private const val secondContainerName = commonContainerName + "2"
private val blockchainDistribution: Map<String, List<BlockchainRid>> = mapOf(
        firstContainerName to listOf(chainRidOf(1)),
        secondContainerName to listOf(chainRidOf(2), chainRidOf(3))
)

/**
 * For the tests below, a docker image is needed. It can be build with e.g:  `mvn (clean) verify -Dskip.surefire.tests`
 */
class DirectoryIT : ManagedModeTest() {

    //    override val awaitDebugLog = true
    private val dockerClient: DockerClient = DefaultDockerClient.fromEnv().apiVersion("v1.41").build()

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

    @Test
    fun testSingleChain() {
        startManagedSystem(1, 0)
        val c1 = startNewBlockchain(setOf(0), setOf(), waitForRestart = false) //location defined in blockchainDistribution
        awaitHeight(c1.chain, 5)
    }

    private fun getCont1Logs(): String {
        val all = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
//        println("all: " + all.map { it.names()?.get(0) }.joinToString())
        val cont1 = all.find { it.names()?.get(0)?.startsWith("/$firstContainerName") ?: false }
        return if (cont1 != null) {
            dockerClient.logs(cont1.id(), LogsParam.stdout(), LogsParam.tail(10))
                    .readFully()
        } else ""
    }

    /**
     * Directory with one signer, no replicas. Signer is signer of all three chains. c0 is run on master node and c1
     * is run in container "cont1" by the subnode. c2, and c3 in cont2
     */
    @Test
    @Ignore
    fun testMultipleChains() {
        startManagedSystem(1, 0)
        val c1 = startNewBlockchain(setOf(0), setOf(), waitForRestart = false)
        val c2 = startNewBlockchain(setOf(0), setOf(), waitForRestart = false)  //location defined in blockchainDistribution
        val c3 = startNewBlockchain(setOf(0), setOf(), waitForRestart = false)  //location defined in blockchainDistribution
        awaitHeight(c1.chain, 0)
        awaitHeight(c2.chain, 0)
        awaitHeight(c3.chain, 0)
    }

    /**
     * With more than one node, docker port, container name and directory for files must be node specific.
     */
    @Ignore
    @Test
    fun testMultipleNodes() {
        startManagedSystem(2, 0)
        val c1 = startNewBlockchain(setOf(0, 1), setOf(), waitForRestart = true)
        awaitHeight(c1.chain, 0)
    }

    /**
     * Assert that ram and cpu limits can be set on the container
     */
    @Ignore
    @Test
    fun testResourceLimits() {
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
                    println("ChainId: $chainId, node idx: $it, subnode height: ${subnode?.subnodeStatus}")
                }
                res
            }

            println("---------- Cont1 logs ----------")
            println(getCont1Logs())
            println("---------- END of Cont1 logs ----------")

            // Stop after 5 min
            if ((System.currentTimeMillis() - start) / 60_000 > 5) {
                running = false
                assertTrue(false,  "awaitHeight: timeout occurred")
            }

        } while (running)

        awaitLog("========= DONE AWAIT ALL ${nodes.size} NODES chain: $chainId, height: $height (i)")
    }

    override fun nodeConfigurationMap(nodeIndex: Int, peerInfo: PeerInfo): Configuration {
        val propertyMap = super.nodeConfigurationMap(nodeIndex, peerInfo)
        val className = TestDirectoryMasterInfraFactory::class.qualifiedName
        val masterHost = System.getenv("POSTCHAIN_TEST_MASTER_HOST") ?: "host.docker.internal"
        val dbHost = System.getenv("POSTCHAIN_TEST_DB_HOST") ?: "localhost"
        propertyMap.setProperty("infrastructure", className)
        propertyMap.setProperty("containerChains.masterPort", 9860 - nodeIndex)
        propertyMap.setProperty("containerChains.masterHost", masterHost)
        propertyMap.setProperty("configDir", System.getProperty("user.dir"))
        propertyMap.setProperty("subnode.database.url", "jdbc:postgresql://$dbHost:5432/postchain")
        propertyMap.setProperty("brid.chainid.1", chainRidOf(1).toHex())
        propertyMap.setProperty("brid.chainid.2", chainRidOf(2).toHex())
        propertyMap.setProperty("brid.chainid.3", chainRidOf(3).toHex())
        propertyMap.setProperty("heartbeat.enabled", false)
        propertyMap.setProperty("heartbeat.timeout", 6_000_000L) //default 60_000
        propertyMap.setProperty("heartbeat.sleep_timeout", 500L)
        propertyMap.setProperty("remote_config.enabled", false)
        return propertyMap
    }
}

class TestMasterCommunicationManager(
        nodeConfig: NodeConfig,
        chainId: Long,
        blockchainRid: BlockchainRid,
        peersCommConfigFactory: PeersCommConfigFactory,
        private val masterConnectionManager: MasterConnectionManager,
        private val dataSource: DirectoryDataSource,
        private val processName: BlockchainProcessName
) : DefaultMasterCommunicationManager(nodeConfig, chainId, blockchainRid, peersCommConfigFactory,
        masterConnectionManager, dataSource, processName) {
    override fun init() {

        val testPacketConsumer = (dataSource as MockDirectoryDataSource).getSubnodeInterceptor(slavePacketConsumer(), blockchainRid)
        val slaveChainConfig = SlaveChainConfig(chainId, blockchainRid, testPacketConsumer)
        masterConnectionManager.connectSlaveChain(processName, slaveChainConfig)
    }
}

class TestMasterSyncInfra(
        nodeConfigProvider: NodeConfigurationProvider,
        nodeDiagnosticContext: NodeDiagnosticContext
) : DefaultMasterSyncInfra(nodeConfigProvider, nodeDiagnosticContext) {
    override fun makeMasterBlockchainProcess(
            processName: BlockchainProcessName,
            chainId: Long,
            blockchainRid: BlockchainRid,
            dataSource: DirectoryDataSource,
            containerChainDir: ContainerChainDir,
            restApiPort: Int
    ): ContainerBlockchainProcess {

        val communicationManager = TestMasterCommunicationManager(
                nodeConfig,
                chainId,
                blockchainRid,
                peersCommConfigFactory,
                connectionManager as MasterConnectionManager,
                dataSource,
                processName
        ).apply { init() }

        return DefaultContainerBlockchainProcess(
                nodeConfig,
                processName,
                chainId,
                blockchainRid,
                restApiPort,
                communicationManager,
                dataSource,
                containerChainDir
        )
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

        val syncInfra = TestMasterSyncInfra(nodeConfigProvider, nodeDiagnosticContext)

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
                                                   val testDataSource: MockDirectoryDataSource)
    : ContainerManagedBlockchainProcessManager(blockchainInfrastructure,
        nodeConfigProvider,
        blockchainConfigProvider,
        nodeDiagnosticContext) {

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
        }
        return result.toSet()
    }

    var lastHeightStarted = ConcurrentHashMap<Long, Long>()

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
        } else { //receiving heights asynchronous from subnodes' status messages
            lastHeightStarted[chainId] = -2L
        }
        return blockchainRid
    }

    fun awaitStarted(nodeIndex: Int, chainId: Long, atLeastHeight: Long) {
        // ask TestPacket consumer for subNode status.
        awaitDebug("++++++ AWAIT node idx: " + nodeIndex + ", chain: " + chainId + ", height: " + atLeastHeight)
        while (lastHeightStarted.get(chainId) ?: -2L < atLeastHeight) {
            lastHeightStarted[chainId] = testDataSource.subnodeInterceptors[chainRidOf(chainId)]!!.subnodeStatus
            sleep(10)
        }
        awaitDebug("++++++ WAIT OVER! node idx: " + nodeIndex + ", chain: " + chainId + ", height: " + atLeastHeight)
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

class TestPacketConsumer(var subconsumer: MsMessageHandler?) : MsMessageHandler {
    var subnodeStatus = -2L

    override fun onMessage(message: MsMessage) {
        // Do things used by tests
        when (message) {
            is MsDataMessage -> {
                val s = SignedMessage.decode(message.xPacket)
                val p = Message.decode<Message>(s.message)
                when (p) {
                    is Status -> {
                        if (p.blockRID != null)
                            subnodeStatus = p.height
                    }
                }
            }
            is MsSubnodeStatusMessage -> {
                subnodeStatus = message.height
            }
        }
        // Do the normal stuff
        subconsumer!!.onMessage(message)
    }
}

class MockDirectoryDataSource(nodeIndex: Int) : MockManagedNodeDataSource(nodeIndex), DirectoryDataSource {

    var ram = 7000_000_000L
    var cpu = 100_000L
    var subnodeInterceptors = mutableMapOf<BlockchainRid, TestPacketConsumer>()


    override fun getConfigurations(blockchainRidRaw: ByteArray): Map<Long, ByteArray> {
        val l = bridToConfs[BlockchainRid(blockchainRidRaw)] ?: return mapOf()
        val confs = mutableMapOf<Long, ByteArray>()
        for (entry in l) {
            val data = entry.value.second
            // try to decode to ensure data is valid
            GtvFactory.decodeGtv(data).asDict()
            confs[entry.key] = data
        }
        return confs
    }

    override fun getContainersToRun(): List<String>? {
        return listOf("system", firstContainerName, secondContainerName)
    }

    override fun getBlockchainsForContainer(containerID: String): List<BlockchainRid>? {
        return blockchainDistribution[containerID]
    }

    override fun getContainerForBlockchain(brid: BlockchainRid): String {
        var res = "system"
        blockchainDistribution.forEach { containerName, bcList ->
            val found = bcList.firstOrNull { it == brid } != null
            if (found) res = containerName
        }
        return res
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

    fun getSubnodeInterceptor(subconsumer: MsMessageHandler, blockchainRid: BlockchainRid): TestPacketConsumer {
        subnodeInterceptors.put(blockchainRid, TestPacketConsumer(subconsumer)) // the real one
        return subnodeInterceptors[blockchainRid]!!
    }

}
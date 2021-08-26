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
import org.junit.Ignore
import org.junit.Test
import java.lang.Thread.sleep
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.assertEquals

const val firstContainerName = "cont1" //for chain 1
const val secondContainerName = "cont2" //for chain 2, 3

/**
 * For the tests below, a docker image is needed. It can be build with e.g:  `mvn (clean) verify -Dskip.surefire.tests`
 *
 */
class DirectoryIT : ManagedModeTest() {

    @Test
    fun testSingleChain() {
        startManagedSystem(1, 0)
        buildBlock(c0)
        val c1 = startNewBlockchain(setOf(0), setOf(), waitForRestart = true)
    }

    /**
     * Directory with one signer, no replicas. Signer is signer of all three chains. c0 is run on master node and c1
     * is run in container "cont1" by the subnode. c2, and c3 in cont2
     */
    @Test
    @Ignore
    fun testMultipleChains() {
        startManagedSystem(1, 0)
        buildBlock(c0, 0)
        val c1 = startNewBlockchain(setOf(0), setOf(), waitForRestart = true)
        val c2 = startNewBlockchain(setOf(0), setOf(), waitForRestart = true)  //c2 in cont2
        val c3 = startNewBlockchain(setOf(0), setOf(), waitForRestart = false)  //c3 in cont2
    }


    /**
     * With more than one node, docker port, container name and directory for files must be node specific.
     */
    @Ignore
    @Test
    fun testMultipleNodes() {
        startManagedSystem(2, 0)
        buildBlock(c0, 0)
        val c1 = startNewBlockchain(setOf(0, 1), setOf(), waitForRestart = true)
    }

    /**
     * Assert that ram and cpu limits can be set on the container
     */
//    @Ignore
    @Test
    fun testResourceLimits() {
        startManagedSystem(1, 0)
        buildBlock(c0, 0)
        val ramLimit = 6_000_000_000L
        val cpuQuotaLimit = 90_000L

        //update dataSource with limit value. This is used when container is created (getResourceLimitForContainer)
        dataSource(0).setLimitsForContainer(firstContainerName, ramLimit, cpuQuotaLimit)


        // If container UUT already exist, remove it
        val dockerClient: DockerClient = DefaultDockerClient.fromEnv().build()
        var listc = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
        listc.forEach {
            if (it.names()?.get(0)?.contains(Regex(firstContainerName))!!) {
                println("removing existing container: " + it.names())
                dockerClient.stopContainer(it.id(), 0)
                dockerClient.removeContainer(it.id())
            }
        }
        startNewBlockchain(setOf(0), setOf(), waitForRestart = true)
        listc = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers())
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

//    System providers create a ‘system’ cluster which includes ‘system’ nodes and has a ‘system’ naked container which will run the directory.
    /**
     * MockDirectoryDataSource is populated with two containers and n0, n1 are block builders for both no replicas.
     * startManagedSystem() starts bc0 in system container.
     * startNewBlockchain() should start bc1 in cont1 container
     */


    override fun createMockDataSource(nodeIndex: Int): MockManagedNodeDataSource {
        return MockDirectoryDataSource(nodeIndex)
    }

    override open fun awaitChainRunning(index: Int, chainId: Long, atLeastHeight: Long) {
        val pm = nodes[index].processManager as TestContainerManagedBlockchainProcessManager
        // await subnode ready for heartbeat
        while (dataSource(index).subnodeInterceptors[chainRidOf(chainId)]?.subnodeStatus == null) {
            buildBlock(c0)
            sleep(500L)
        }
        while (dataSource(index).subnodeInterceptors[chainRidOf(chainId)]?.subnodeStatus == -2L) {
            buildBlock(c0)
            sleep(500L)
        }
        pm.awaitStarted(index, chainId, atLeastHeight)
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
        propertyMap.setProperty("heartbeat.enabled", true)
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
        private val peersCommConfigFactory: PeersCommConfigFactory,
        private val masterConnectionManager: MasterConnectionManager,
        private val dataSource: DirectoryDataSource,
        private val processName: BlockchainProcessName
) : DefaultMasterCommunicationManager(nodeConfig, chainId, blockchainRid, peersCommConfigFactory, masterConnectionManager, dataSource, processName) {
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
   open var subnodeStatus = -2L

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
                if (message.blockchainRid != null)
                    subnodeStatus =  message.height
            }
        }
        // Do the normal stuff
        subconsumer!!.onMessage(message)
    }

    fun isStarted(): Boolean {
        return (subnodeStatus > -2L)
    }
}

class MockDirectoryDataSource(nodeIndex: Int) : MockManagedNodeDataSource(nodeIndex), DirectoryDataSource {

    var ram = 7000_000_000L
    var cpu = 100_000L
    var subnodeInterceptors = mutableMapOf<BlockchainRid, TestPacketConsumer>()


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

    //chain 0 in system container, chain1 in cont1 container. chain2 and chain3 in cont2 container.
    override fun getBlockchainsForContainer(containerID: String): List<BlockchainRid>? {
        if (containerID == firstContainerName) {
            return listOf(chainRidOf(1))
        } else if (containerID == secondContainerName) {
            return listOf(chainRidOf(2), chainRidOf(3))
        } else {
            return listOf(chainRidOf(0))
        }
    }

    override fun getContainerForBlockchain(brid: BlockchainRid): String {
        if (brid == chainRidOf(1)) {
            return firstContainerName
        } else if ((brid == chainRidOf(2)) or (brid == chainRidOf(3))) {
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

    fun getSubnodeInterceptor(subconsumer: MsMessageHandler, blockchainRid: BlockchainRid): TestPacketConsumer {

        subnodeInterceptors.put(blockchainRid, TestPacketConsumer(subconsumer)) // the real one
        return subnodeInterceptors[blockchainRid]!!
    }

}
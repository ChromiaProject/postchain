package net.postchain.integrationtest.sync

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
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.managed.DirectoryDataSource
import net.postchain.managed.ManagedNodeDataSource
import org.apache.commons.configuration2.Configuration
import org.junit.Ignore
import org.junit.Test
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

class DirectoryTest : ManagedModeTest() {

    @Ignore
    @Test
    fun dummy() {
        startDirectory()
        buildBlock(c0, 0)
        val c1 = startNewBlockchain(setOf(0, 1), setOf())
        buildBlock(c1, 10)
    }

//    System providers create a ‘system’ cluster which includes ‘system’ nodes and has a ‘system’ naked container which will run the directory.
    fun startDirectory() {
    //Create system cluster with system nodes n0, n1
    //Create naked system container
    //start bc0 in system container: n0, n1 build blocks, no replicas
    startManagedSystem(2, 0)



}
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

    override fun getBlockchainsShouldBeLaunched(): Set<Long> {
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
    override fun startBlockchain(chainId: Long): BlockchainRid? {
        val blockchainRid = super.startBlockchain(chainId)
        if (blockchainRid == null) {
            return null
        }
        val process = blockchainProcesses[chainId]!!
        val queries = process.getEngine().getBlockQueries()
        val height = queries.getBestHeight().get()
        lastHeightStarted[chainId] = height
        return blockchainRid
    }

    fun awaitStarted(chainId: Long, atLeastHeight: Long) {
        while (lastHeightStarted.get(chainId) ?: -2L < atLeastHeight) {
            Thread.sleep(10)
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
    override fun getContainersToRun(): List<String>? {
//        return listOf()
        return listOf("system","cont1")
    }

    //chain 0 in system container, chain1 in cont1 container.
    override fun getBlockchainsForContainer(containerID: String): List<BlockchainRid>? {
        if (containerID == "cont1") {
            return listOf(chainRidOf(1))
        }
        else {
            return listOf(chainRidOf(0))
        }
//        return listOf()
    }

    override fun getResourceLimitForContainer(containerID: String): Map<String, Long>? {
        if (containerID == "cont1") {
            return mapOf("storage" to 10L, "ram" to 10L, "cpu" to 10L)
        }
        return mapOf() //no limits for naked system container.
    }
}
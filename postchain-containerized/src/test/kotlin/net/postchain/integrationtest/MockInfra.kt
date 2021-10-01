package net.postchain.integrationtest

import net.postchain.core.BlockchainRid
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
import net.postchain.devtools.TestBlockchainConfigurationProvider
import net.postchain.devtools.awaitDebug
import net.postchain.devtools.chainIidOf
import net.postchain.devtools.chainRidOf
import net.postchain.ebft.message.Message
import net.postchain.ebft.message.SignedMessage
import net.postchain.ebft.message.Status
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
import java.util.concurrent.ConcurrentHashMap

/**
 * File contains overridden, mocked, and stubbed components of Blockchain and Sync Infrastructures
 */

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
            Thread.sleep(10)
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

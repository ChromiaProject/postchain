package net.postchain.integrationtest.sync

import net.postchain.StorageBuilder
import net.postchain.base.BaseBlockchainConfigurationData
import net.postchain.base.PeerInfo
import net.postchain.base.Storage
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DatabaseAccessFactory
import net.postchain.base.runStorageCommand
import net.postchain.common.toHex
import net.postchain.config.app.AppConfig
import net.postchain.core.AppContext
import net.postchain.core.BlockchainRid
import net.postchain.core.NODE_ID_NA
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.KeyPairHelper
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.awaitHeight
import net.postchain.devtools.utils.configuration.*
import net.postchain.devtools.utils.configuration.pre.BlockchainPreSetup
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import net.postchain.network.x.XPeerID
import org.junit.Assert.assertArrayEquals

open class AbstractSyncTest : IntegrationTestSetup() {
    var signerCount: Int = -1
    open var mustSyncUntil = -1L


    /**
     * Does everything from building configuration to starting the nodes.
     *
     * Note:
     * This is a bit different from hov the [SystemSetupFactory] works. In the factory everything is created at
     * once and started, but here we want to be able to re-start nodes and re-generate their setups.
     *
     * // TODO: Olle: The remaining task is to go (NodeSetup -> PeerInfo) instead of (PeerInfo -> NodeSetup), then lift this into [SystemSetupFactory]
     *
     * @param signerNodeCount is the number of signers we need
     * @param replicaCount is the number of replicas we need
     * @return the newly created [NodeSetup]s.
     */
    protected fun runNodes(signerNodeCount: Int, replicaCount: Int): Array<NodeSetup> {
        signerCount = signerNodeCount
        configOverrides.setProperty("fastsync.exit_delay", "2000")

        // -------
        // TODO: Olle Implement this instead of step 1-4
        // val sysSetup = SystemSetupFactory.buildManagedSystemSetup(signerNodeCount, replicaCount)
        // -------

        val peerInfos = createPeerInfosWithReplicas(signerNodeCount, replicaCount)

        // 1. Get BCSetup
        val chainId = 0
        val blockchainPreSetup =
            BlockchainPreSetup.simpleBuild(chainId, (0 until signerNodeCount).map { NodeSeqNumber(it) })
        val blockchainSetup = BlockchainSetup.buildFromGtv(chainId, blockchainPreSetup.toGtvConfig(mapOf()))
        System.out.println("++ BC Setup: ${blockchainSetup.rid.toShortHex()} , strategy: ${blockchainSetup.bcGtv[BaseBlockchainConfigurationData.KEY_BLOCKSTRATEGY]} ")

        // 2. Get NodeSetup
        var i = 0
        val nodeSetups = peerInfos.associate { peerInfo ->
            NodeSeqNumber(i) to createNodeSetup(i++, peerInfo)
        }

        // 3. Combine (1) and (2) to get SystemSetup
        val systemSetup = SystemSetup(
            nodeSetups,
            mapOf(chainId to blockchainSetup),
            true,
            "managed",
            "unused", // Doesn't matter, not used as of now
            "base/ebft",
            true
        )

        this.systemSetup = systemSetup

        // 4. Add node config and fix DB
        nodeSetups.values.forEach { nodeSetup ->
            configureSingleNodeSetup(nodeSetup)
            fixDbForSingleNodeSetup(nodeSetup, peerInfos, true, blockchainSetup.rid)
        }

        // 5. Start everything
        createTestNodesAndStartAllChainsFromSystem(systemSetup)

        return nodeSetups.values.toTypedArray()
    }

    val DEFAULT_STORAGE_FACTORY: (AppConfig) -> Storage = {
        StorageBuilder.buildStorage(it, NODE_ID_NA, false)
    }

    /** This function is used instead of the default one, to prepare the local database tables before node is started.
     * By introducing a prepareBlockchainOnNode function, preparations is separated from the running. Thereby (as in this
     * example, we can populate the database table must_sync_until)
     **/
    open fun prepareBlockchainOnNode(setup: BlockchainSetup, node: PostchainTestNode) {
        node.addBlockchain(setup)
        node.mapBlockchainRID(setup.chainId.toLong(), setup.rid)
        node.setMustSyncUntil(setup.chainId.toLong(), setup.rid, mustSyncUntil)
    }

    protected fun restartNodeClean(nodeIndex: Int, brid: BlockchainRid) {
        val nodeSetup = systemSetup.nodeMap[NodeSeqNumber(nodeIndex)]!!
        val peerInfoMap = nodeSetup.configurationProvider!!.getConfiguration().peerInfoMap
        val peers = peerInfoMap.values.toTypedArray()

        // Shutdown the node
        nodes[nodeIndex].shutdown()

        // Start over
        val newSetup = createNodeSetup(nodeIndex, peers[nodeIndex])
        configureSingleNodeSetup(newSetup)
        fixDbForSingleNodeSetup(newSetup, peers, true, brid)
        val blockchainSetup = systemSetup.blockchainMap[0]
        blockchainSetup!!.prepareBlockchainOnNode = { setup, node -> prepareBlockchainOnNode(setup, node) }
        val testNode = newSetup.toTestNodeAndStartAllChains(systemSetup, false)
        updateCache(newSetup, testNode)
    }

    protected fun startOldNode(nodeIndex: Int, peerInfoMap: Map<XPeerID, PeerInfo>, brid: BlockchainRid) {
        val peers = peerInfoMap.values.toTypedArray()
        val newSetup = createNodeSetup(nodeIndex, peers[nodeIndex])
        configureSingleNodeSetup(newSetup)
        fixDbForSingleNodeSetup(newSetup, peers, false, brid)
        val testNode = newSetup.toTestNodeAndStartAllChains(systemSetup, false)
        updateCache(newSetup, testNode)
    }

    /**
     * @return the new [NodeSetup] with key pair from [PeerInfo]
     */
    private fun createNodeSetup(nodeIndex: Int, peerInfo: PeerInfo): NodeSetup {
        val signer = if (nodeIndex < signerCount) setOf(0) else setOf()
        val replica = if (nodeIndex >= signerCount) setOf(0) else setOf()
        return NodeSetup(
            NodeSeqNumber(nodeIndex),
            signer,
            replica,
            peerInfo.pubKey.toHex(),
            KeyPairHelper.privKey(peerInfo.pubKey).toHex()  // Derive from pubkey
        )
    }

    /**
     * Create a [NodeConfigurationProvider] and add it to the [NodeSetup].
     */
    private fun configureSingleNodeSetup(nodeSetup: NodeSetup) {

        // 1. The node specific overrides will be stored in the [NodeSetup], so they can be used in step 2 when
        // the [NodeConfigurationProvider] is created.
        addNodeConfigurationOverrides(nodeSetup)

        // 2. Create the provider
        val nodeConfigProvider = NodeConfigurationProviderGenerator.buildFromSetup(
            getTestName(),
            this.configOverrides, // Don't think these test ever use this
            nodeSetup,
            systemSetup
        )
        nodeSetup.configurationProvider = nodeConfigProvider // New way of fetching the config
    }

    /**
     * ??
     */
    private fun fixDbForSingleNodeSetup(
        nodeSetup: NodeSetup,
        peerInfos: Array<PeerInfo>,
        wipeDb: Boolean,
        brid: BlockchainRid
    ) {
        val appConfig = nodeSetup.configurationProvider!!.getConfiguration().appConfig

        if (wipeDb) {
            System.out.println("++ Wiping DB for Node: ${nodeSetup.sequenceNumber.nodeNumber}, BC: ${brid.toShortHex()}")
        } else {
            System.out.println("++ Building DB (no wipe) for Node: ${nodeSetup.sequenceNumber.nodeNumber}, BC: ${brid.toShortHex()}")
        }

        StorageBuilder.buildStorage(appConfig, nodeSetup.sequenceNumber.nodeNumber, wipeDb).close()

        // TODO: Olle: Not sure what's going on here
        if (wipeDb) {
            runStorageCommand(appConfig) {
                val ctx = it
                val dbAccess = DatabaseAccessFactory.createDatabaseAccess(appConfig.databaseDriverclass)
                peerInfos.forEachIndexed { index, peerInfo ->
                    val isPeerSigner = index < signerCount
                    addPeerInfo(dbAccess, ctx, peerInfo, brid, isPeerSigner)
                }
            }
        }
    }

    open protected fun addPeerInfo(
        dbAccess: DatabaseAccess,
        ctx: AppContext,
        peerInfo: PeerInfo,
        brid: BlockchainRid,
        isPeerSigner: Boolean
    ) {
        dbAccess.addPeerInfo(ctx, peerInfo)
        if (!isPeerSigner) {
            dbAccess.addBlockchainReplica(ctx, brid.toHex(), peerInfo.pubKey.toHex())
        }
    }


    /**
     * Use the [SystemSetup] to create [PostchainTestNode] and run all chains
     */
    private fun createTestNodesAndStartAllChainsFromSystem(sysSetup: SystemSetup) {
        for (nodeSetup in sysSetup.nodeMap.values) {
            val newPTNode = nodeSetup.toTestNodeAndStartAllChains(sysSetup, false)
            updateCache(nodeSetup, newPTNode)
        }
    }

    private fun n(index: Int): String {
        val p = nodes[index].pubKey
        return p.substring(0, 4) + ":" + p.substring(64)
    }

    /**
     * The idea here is to:
     *
     * 1. run nodes up to the height just before the given "blocksToSync",
     * 2. stop the nodes given in the "stopIndex" list.
     * 3. kill the nodes given in the "syncIndex" list (kill means wipe their DB, so all blocks have been lost).
     * 4. wait until the nodes in "syncIndex" list get back to the height they had
     * 5. start the nodes in "stopIndex" list
     * 6. build one more block
     *
     * This is actually a rather good way to test fast sync, b/c a "real" node might get wiped and we will get
     * into this situation.
     * Note: another common situation is when a new node wants to join the network.
     *
     * @param signerCount amount of signer nodes
     * @param replicaCount amount of replica nodes
     * @param syncIndex which nodes to clean+restart and try to sync without help from stop index nodes
     * @param stopIndex which nodes to stop
     * @param blocksToSync height when sync nodes are wiped.
     */
    fun runSyncTest(signerCount: Int, replicaCount: Int, syncIndex: Set<Int>, stopIndex: Set<Int>, blocksToSync: Int) {
        val nodeSetups = runNodes(signerCount, replicaCount) // This gives us SystemSetup

        val blockchainRid = nodes[0].getBlockchainRid(0)!!
        logger.debug { "++ All nodes started" }
        buildBlock(0, blocksToSync - 1L)
        logger.debug { "++ All nodes have block ${blocksToSync - 1}" }

        val expectedBlockRid = nodes[0].blockQueries(0).getBlockRid(blocksToSync - 1L).get()
        val peerInfos = nodeSetups[0].configurationProvider!!.getConfiguration().peerInfoMap
        stopIndex.forEach {
            logger.debug { "++ Shutting down ${n(it)}" }
            nodes[it].shutdown()
            logger.debug { "++ Shutting down ${n(it)} done" }
        }
        syncIndex.forEach {
            logger.debug { "++ Restarting clean ${n(it)}" }
            restartNodeClean(it, blockchainRid)
            logger.debug { "++ Restarting clean ${n(it)} done" }
        }

        syncIndex.forEach {
            logger.debug { "++ Awaiting height ${blocksToSync - 1L} on ${n(it)}" }
            nodes[it].awaitHeight(0, blocksToSync - 1L)
            val actualBlockRid = nodes[it].blockQueries(0).getBlockRid(blocksToSync - 1L).get()
            assertArrayEquals(expectedBlockRid, actualBlockRid)
            logger.debug { "++ Awaiting height ${blocksToSync - 1L} on ${n(it)} done" }
        }

        stopIndex.forEach {
            logger.debug { "++ Start ${n(it)} again" }
            startOldNode(it, peerInfos, blockchainRid)
        }
        awaitHeight(0, blocksToSync - 1L)
        buildBlock(0, blocksToSync.toLong())
        logger.debug { "++ All nodes have block $blocksToSync" }
    }
}
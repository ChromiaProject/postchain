package net.postchain.base.icmf

import net.postchain.base.BlockchainRelatedInfo
import net.postchain.config.blockchain.SimpleConfReaderMock
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainRid
import org.easymock.EasyMock
import org.junit.Test
import org.easymock.EasyMockSupport
import org.junit.Assert.assertTrue
import kotlin.test.assertEquals

/**
 * Since we support both manual and managed mode, I made one scenario for each.
 */
class IcmfControllerTest : EasyMockSupport() {

    /**
     * This is a manual mode scenario test on [IcmfController], testing few things at once.
     *
     * Scenario:
     * We only use one source chain (=1) and one listener chain (=2).
     * 1. Chain 1 starts first, calls "maybeConnect()", get one pipe
     *   1.x,a,b: make sure things get updated
     * 2. Chain 2 starts later, calls "maybeConnect()", get no pipes
     *   2.a: make sure things get updated
     */
    @Test
    fun manualConfigTest() {
        // Setup PipeConnSync
        val bcRid2 = BlockchainRid.buildRepeat(2)
        val chain1ConfMock = SimpleConfReaderMock(
                null,
                listOf(bcRid2) // Idea is that chain1 will have chain2 as a listener
        )
        val chain2ConfMock = SimpleConfReaderMock(
            null   // Chain2 doesn't have any configuration
        )
        val connSync = IcmfPipeConnectionSync(chain1ConfMock) // Using the mock for chain1 initially

        // Setup Controller
        val controller = IcmfController()
        controller.initialize(connSync)

        val dummyHeight = 17L

        // Hate EasyMock and prefer to do my own dummy classes, but here I had to
        val mockProcessChain1: BlockchainProcess = mockBlockchainProcess(1L)
        val mockProcessChain2: BlockchainProcess = mockBlockchainProcess(2L)
        replayAll()

        // ---------
        // Test 1.
        // ---------
        // Call "maybeConnect()" to see if we get the correct pipes created
        val chain1NewPipes = controller.maybeConnect(mockProcessChain1, dummyHeight)

        assertEquals(1, chain1NewPipes.size)
        assertTrue(
            hasPipeId(
                "1-02:02", // ICMF doesn't know the chainIid, so we'll use the BcRID for the name
                chain1NewPipes
            )
        )

        // ---------
        // Test 1.x
        // ---------
        // If we try to connect same process (=chain) again, nothing happens (only get a bunch o warnings)
        val noPipes = controller.maybeConnect(mockProcessChain1, dummyHeight)
        assertEquals(0, noPipes.size)

        // ---------
        // Test 1.a
        // ---------
        // If we ask the Receiver for the listener's pipes we only get one pipe
        val pipesForChain2 = controller.icmfReceiver.getAllPipesForListenerChain(bcRid2)
        assertEquals(1, pipesForChain2.size)
        val pipeR = pipesForChain2[0]
        assertEquals(1, pipeR.sourceChainIid)
        assertEquals(bcRid2, pipeR.listenerChainInfo.blockchainRid)

        // ---------
        // Test 1.b
        // ---------
        // If we ask the Dispatcher about the source we should see it too
        val pipesDispatch = controller.icmfDispatcher.getAllPipesForSourceChain(1)
        assertEquals(1, pipesDispatch.size)
        assertTrue(hasPipeId("1-02:02", chain1NewPipes))

        // ---------
        // Test 2.
        // ---------
        // Call "maybeConnect()" for chain 2
        connSync.setSimpleConfReader(chain2ConfMock) // Now, we're running chain2 so we manually replace the mock for the conf file reader
        val chain2Pipes = controller.maybeConnect(mockProcessChain2, dummyHeight)
        assertEquals(0, chain2Pipes.size) // This is just a listener chain, so no new pipes will come out

        // ---------
        // Test 2.a
        // ---------
        val pipesForChain2New = controller.icmfReceiver.getAllPipesForListenerChain(bcRid2)
        assertEquals(1, pipesForChain2New.size)
        val pipeRNew = pipesForChain2New[0]
        assertEquals(1, pipeRNew.sourceChainIid)
        assertEquals(2, pipeRNew.listenerChainInfo.chainId) // At this point the Receiver will know chain2's internal ID.
    }

    /**
     * This is a managed mode scenario test on [IcmfController], testing this:
     *
     * 0. We set up 3 fake listener chains
     * 1. Call "maybeConnect()" on a new source chain (=4) to see if we get these listeners back for a new (unseen) source chain (=4)
     *   1.a-b. Make sure the [IcmfDispatcher] and [IcmfReceiver] get's updated
     * 3. Add another listener "maybeConnect() (=5)"
     * 4. Call "maybeConnect()" again on a new source (=6) to see what pipes we get
     *   4.a-b. Make sure the [IcmfDispatcher] and [IcmfReceiver] get's updated
     */
    @Test
    fun managedTest() {
        val chain1 = BlockchainRelatedInfo(BlockchainRid.buildRepeat(1), null, 1L)
        val chain2 = BlockchainRelatedInfo(BlockchainRid.buildRepeat(2), null, 2L)
        val chain3 = BlockchainRelatedInfo(BlockchainRid.buildRepeat(3), null, 3L)
        val chain4 = BlockchainRelatedInfo(BlockchainRid.buildRepeat(4), null, 4L)
        val chain5 = BlockchainRelatedInfo(BlockchainRid.buildRepeat(5), null, 5L)

        val dummyHeight = 17L

        // Conf mocks representing managed mode
        val sourceConfMock = SimpleConfReaderMock(null) // No conf
        val listenerConfMock = SimpleConfReaderMock("all") // This conf mock will say that this is a listener for all chains

        // Hate EasyMock and prefer to do my own dummy classes, but here I had to
        val mockProcessChain4: BlockchainProcess = mockBlockchainProcess(4L)
        val mockProcessChain5: BlockchainProcess = mockBlockchainProcess(5L)
        val mockProcessChain6: BlockchainProcess = mockBlockchainProcess(6L)
        replayAll()

        // -------------------
        // Setup PipeConnSync
        // -------------------
        // (Using mock to avoid the DB)
        val connSync = IcmfPipeConnectionSync(listenerConfMock)

        // ---------
        // Setup Controller
        // ---------
        val controller = IcmfController()
        controller.initialize(connSync)

        val initialListenerChains = setOf(
            chain1,
            chain2,
            chain3
        )
        val allChains = initialListenerChains.toMutableSet()
        // Using the SimpleConfReader, these will all look like listener chains
        controller.setAllChains(allChains) // We have to start with the chains we got from Chain 0 (i.e. chain 1-3)

        // ---------
        // Test 1.
        // ---------
        // Put source config mock
        connSync.setSimpleConfReader(sourceConfMock)

        // Call "maybeConnect()" to see if we get the correct pipes created
        val chain4Pipes = controller.maybeConnect(mockProcessChain4, dummyHeight) // Use the mock for process 4
        assertEquals(3, chain4Pipes.size, "A new source should get connected to all existing listeners")
        // What order the pipes are sorted in doesn't matter to us
        assertTrue(hasPipeId( "4-1",  chain4Pipes))
        assertTrue(hasPipeId( "4-2",  chain4Pipes))
        assertTrue(hasPipeId( "4-3",  chain4Pipes))

        // ---------
        // Test 1.a Receiver
        // ---------
        // If we ask the Receiver for the listener's pipes we only get one pipe
        val pipesForChain1 = controller.icmfReceiver.getAllPipesForListenerChain(chain1.blockchainRid)
        assertEquals(1, pipesForChain1.size)
        val pipeR = pipesForChain1[0]
        assertEquals(4, pipeR.sourceChainIid)
        assertEquals(1, pipeR.listenerChainInfo.chainId)

        // ---------
        // Test 1.b Dispatcher
        // ---------
        // If we ask the Dispatcher about the source we should see it too
        val pipesDispatch4 = controller.icmfDispatcher.getAllPipesForSourceChain(4)
        assertEquals(3, pipesDispatch4.size)
        assertTrue(hasPipeId( "4-1",  pipesDispatch4))
        assertTrue(hasPipeId( "4-2",  pipesDispatch4))
        assertTrue(hasPipeId( "4-3",  pipesDispatch4))

        // ---------
        // Test 2.
        // ---------
        // Put listener config mock
        connSync.setSimpleConfReader(listenerConfMock)

        // Call "maybeConnect()" to add an unseen listener to the mix
        val chain5Pipes = controller.maybeConnect(mockProcessChain5, dummyHeight)
        assertEquals(3, chain5Pipes.size) // QUESTION: Olle: currently a new listener chain will produce data for other listener chains. This might on might not be correct depending on what we choose.

        // ---------
        // Test 3.
        // ---------
        // Put source config mock
        connSync.setSimpleConfReader(sourceConfMock)

        // Call "maybeConnect()" again to see if new chain (5) will get picked up
        val chain6Pipes = controller.maybeConnect(mockProcessChain6, dummyHeight)
        assertEquals(4, chain6Pipes.size)
        assertTrue(hasPipeId( "6-1",  chain6Pipes))
        assertTrue(hasPipeId( "6-2",  chain6Pipes))
        assertTrue(hasPipeId( "6-3",  chain6Pipes))
        assertTrue(hasPipeId( "6-5",  chain6Pipes))

        // ---------
        // Test 4.
        // ---------
        // If we try to connect same process (=chain) again, nothing happens (only get a bunch o warnings)
        val noPipes = controller.maybeConnect(mockProcessChain6, dummyHeight)
        assertEquals(0, noPipes.size)

        // ---------
        // Test 4.a Receiver
        // ---------
        // If we ask the Receiver for the listener's pipes we only get one pipe
        val pipesForChain1_new = controller.icmfReceiver.getAllPipesForListenerChain(chain1.blockchainRid)
        assertEquals(3, pipesForChain1_new.size)
        assertTrue(hasPipeId( "4-1",  pipesForChain1_new))
        assertTrue(hasPipeId( "5-1",  pipesForChain1_new)) // means we'll get data from chain 5, which is itself a listener
        assertTrue(hasPipeId( "6-1",  pipesForChain1_new))

        // ---------
        // Test 4.b Dispatcher
        // ---------
        // If we ask the Dispatcher about the source we should see it too
        val pipesDispatch = controller.icmfDispatcher.getAllPipesForSourceChain(6)
        assertEquals(4, pipesDispatch.size)
        assertTrue(hasPipeId( "6-1",  pipesDispatch))
        assertTrue(hasPipeId( "6-2",  pipesDispatch))
        assertTrue(hasPipeId( "6-3",  pipesDispatch))
        assertTrue(hasPipeId( "6-5",  pipesDispatch))
    }


    /**
     * This will mock the entire BC process and the config.
     * Have to mock this, sorry, knew this would be hard to test :-)
     *
     * @param chainIid is the chain we pretend this process is for.
     */
    private fun mockBlockchainProcess(chainIid: Long): BlockchainProcess {
        val bcRid = BlockchainRid.buildRepeat(chainIid.toByte())

        val mockProcess: BlockchainProcess = mock(BlockchainProcess::class.java)
        val mockEngine: BlockchainEngine = mock(BlockchainEngine::class.java)
        val mockConfig: BlockchainConfiguration = mock(BlockchainConfiguration::class.java)
        EasyMock.expect(mockProcess.getEngine()).andReturn(mockEngine).anyTimes()
        EasyMock.expect(mockEngine.getConfiguration()).andReturn(mockConfig).anyTimes()
        EasyMock.expect(mockConfig.chainID).andReturn(chainIid).anyTimes()
        EasyMock.expect(mockConfig.blockchainRid).andReturn(bcRid).anyTimes()

        return mockProcess
    }

    private fun hasPipeId(lookForPipeId: String, pipes: List<IcmfPipe>): Boolean =
        pipes.first { lookForPipeId == it.pipeId } != null
}
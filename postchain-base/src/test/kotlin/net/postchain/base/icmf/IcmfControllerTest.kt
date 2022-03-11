package net.postchain.base.icmf

import net.postchain.base.BlockchainRelatedInfo
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainRid
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.test.assertEquals

/**
 * Since we support both manual and managed mode, I made one scenario for each.
 */
class IcmfControllerTest {

    /**
     * This is a managed mode scenario test on [IcmfController], testing this:
     *
     * - We set up 3 fake listener chains
     * - We set up 3 fake source chains
     * - Start them in random order, and we should get the correct amount of pipes, defined by what chains that are
     *   running at the moment
     */
    @Test
    fun happyScenarioTest() {
        // These are listener chains
        val chain1 = BlockchainRelatedInfo(BlockchainRid.buildRepeat(1), null, 1L)
        val chain2 = BlockchainRelatedInfo(BlockchainRid.buildRepeat(2), null, 2L)
        val chain3 = BlockchainRelatedInfo(BlockchainRid.buildRepeat(3), null, 3L)
        val chain4 = BlockchainRelatedInfo(BlockchainRid.buildRepeat(4), null, 4L)
        val chain5 = BlockchainRelatedInfo(BlockchainRid.buildRepeat(5), null, 5L)
        val chain6 = BlockchainRelatedInfo(BlockchainRid.buildRepeat(6), null, 6L)

        val dummyHeight = 17L

        // Hate EasyMock and prefer to do my own dummy classes, but here I had to
        val mockProcessChain1: BlockchainProcess = mockBlockchainProcess(chain1.chainId!!, "10") // It's a listener
        val mockProcessChain2: BlockchainProcess = mockBlockchainProcess(chain2.chainId!!, "10") // It's a listener
        val mockProcessChain3: BlockchainProcess = mockBlockchainProcess(chain3.chainId!!, "5") // It's a listener of lower rank

        val mockProcessChain4: BlockchainProcess = mockBlockchainProcess(chain4.chainId!!, null) // It's a source, so listenerConfig is "null"
        val mockProcessChain5: BlockchainProcess = mockBlockchainProcess(chain5.chainId!!, null)
        val mockProcessChain6: BlockchainProcess = mockBlockchainProcess(chain6.chainId!!, null)

        // ---------
        // Setup Controller
        // ---------
        val controller = IcmfController()

        // -------------------
        // Setup Fetchers (don't care about messages for this test)
        // -------------------
        controller.setFetcherForListenerChain(chain1.chainId!!, MockFetcher())
        controller.setFetcherForListenerChain(chain2.chainId!!, MockFetcher())
        controller.setFetcherForListenerChain(chain3.chainId!!, MockFetcher())
        controller.setFetcherForListenerChain(chain4.chainId!!, MockFetcher())
        controller.setFetcherForListenerChain(chain5.chainId!!, MockFetcher())
        controller.setFetcherForListenerChain(chain6.chainId!!, MockFetcher())


        // ----------------------------------
        // Test 1, start a source without any listeners
        // ----------------------------------
        // Call "maybeConnect()" to see if we get the correct pipes created
        val chain4Pipes = controller.maybeConnect(mockProcessChain4, dummyHeight) // Use the mock for process 4
        assertEquals(0, chain4Pipes.size, "A new source cannot connect to unstarted listeners")

        // ---------
        // Test 1.a Receiver
        // ---------
        // If we ask the Receiver for the listener's pipes we get none
        val pipesForChain1_test1 = controller.icmfReceiver.getAllPipesForListenerChain(chain1.chainId!!)
        assertEquals(0, pipesForChain1_test1.size)

        // ---------
        // Test 1.b Dispatcher
        // ---------
        val pipesDispatch4_test1 = controller.icmfDispatcher.getAllPipesForSourceChain(4)
        assertEquals(0, pipesDispatch4_test1.size)

        // ----------------------------------
        // Test 2, start a listener
        // ----------------------------------
        // Call "maybeConnect()" to see if we get the correct pipes created
        val chain1Pipes = controller.maybeConnect(mockProcessChain1, dummyHeight)
        assertEquals(1, chain1Pipes.size, "The new listener should find the source")
        assertTrue(hasPipeId( "4-1",  chain1Pipes))

        // ---------
        // Test 2.a Receiver
        // ---------
        val pipesForChain1_test2 = controller.icmfReceiver.getAllPipesForListenerChain(chain1.chainId!!)
        assertEquals(1, pipesForChain1_test2.size)
        val pipeR_test2 = pipesForChain1_test2[0]
        assertEquals(4, pipeR_test2.sourceChainIid)
        assertEquals(1, pipeR_test2.listenerChainIid)

        // ---------
        // Test 2.b Dispatcher
        // ---------
        // If we ask the Dispatcher about the source we should see it too
        val pipesDispatch4_test2 = controller.icmfDispatcher.getAllPipesForSourceChain(4)
        assertEquals(1, pipesDispatch4_test2.size)
        assertTrue(hasPipeId( "4-1",  pipesDispatch4_test2))

        // ----------------------------------
        // Test 3, start a listener
        // ----------------------------------
        // Call "maybeConnect()" to see if we get the correct pipes created
        val chain2Pipes = controller.maybeConnect(mockProcessChain2, dummyHeight)
        assertEquals(1, chain2Pipes.size, "The new listener should find the source, but should NOT " +
                " connect to the other listener, since they are of same height")
        assertTrue(hasPipeId( "4-2",  chain2Pipes))

        // ---------
        // Test 3.a Receiver
        // ---------
        val pipesForChain2_test3 = controller.icmfReceiver.getAllPipesForListenerChain(chain2.chainId!!)
        assertEquals(1, pipesForChain2_test3.size)
        val pipeR_test3 = pipesForChain2_test3[0]
        assertEquals(4, pipeR_test3.sourceChainIid)
        assertEquals(2, pipeR_test3.listenerChainIid)

        // ---------
        // Test 3.b Dispatcher
        // ---------
        // If we ask the Dispatcher about the source we should see it too
        val pipesDispatch4_test3 = controller.icmfDispatcher.getAllPipesForSourceChain(4)
        assertEquals(2, pipesDispatch4_test3.size)
        assertTrue(hasPipeId( "4-1",  pipesDispatch4_test3))
        assertTrue(hasPipeId( "4-2",  pipesDispatch4_test3))

        // ----------------------------------
        // Test 5, start another source
        // ----------------------------------
        val chain5Pipes = controller.maybeConnect(mockProcessChain5, dummyHeight)
        assertEquals(2, chain5Pipes.size, "A new source should get connected to all existing listeners")
        // What order the pipes are sorted in doesn't matter to us
        assertTrue(hasPipeId( "5-1",  chain5Pipes))
        assertTrue(hasPipeId( "5-2",  chain5Pipes))

        // ---------
        // Test 5.a Receiver
        // ---------
        val pipesForChain1 = controller.icmfReceiver.getAllPipesForListenerChain(chain1.chainId!!)
        assertEquals(2, pipesForChain1.size)
        assertTrue(hasPipeId( "4-1",  pipesForChain1))
        assertTrue(hasPipeId( "5-1",  pipesForChain1))

        // ---------
        // Test 5.b Dispatcher
        // ---------
        val pipesDispatch5 = controller.icmfDispatcher.getAllPipesForSourceChain(5)
        assertEquals(2, pipesDispatch5.size)
        assertTrue(hasPipeId( "5-1",  pipesDispatch5))
        assertTrue(hasPipeId( "5-2",  pipesDispatch5))

        // ---------
        // Test 6
        // ---------
        // What makes chain 3 interesting is its listener level, make it available to other listeners
        val chain3Pipes = controller.maybeConnect(mockProcessChain3, dummyHeight)
        assertEquals(4, chain3Pipes.size)
        assertTrue(hasPipeId( "4-3",  chain3Pipes))
        assertTrue(hasPipeId( "5-3",  chain3Pipes))
        assertTrue(hasPipeId( "3-1",  chain3Pipes)) // This is key, listener 3 -> listener 1
        assertTrue(hasPipeId( "3-2",  chain3Pipes)) // This is key, listener 3 -> listener 2

        // ---------
        // Test 6.a Receiver
        // ---------
        val pipesForChain1_test6 = controller.icmfReceiver.getAllPipesForListenerChain(chain1.chainId!!)
        assertEquals(3, pipesForChain1_test6.size)
        assertTrue(hasPipeId( "4-1",  pipesForChain1_test6))
        assertTrue(hasPipeId( "5-1",  pipesForChain1_test6))
        assertTrue(hasPipeId( "3-1",  pipesForChain1_test6)) // This is key, listener 3 -> listener 1

        // ---------
        // Test 7
        // ---------
        val chain6Pipes = controller.maybeConnect(mockProcessChain6, dummyHeight)
        assertEquals(3, chain6Pipes.size)
        assertTrue(hasPipeId( "6-1",  chain6Pipes))
        assertTrue(hasPipeId( "6-2",  chain6Pipes))
        assertTrue(hasPipeId( "6-3",  chain6Pipes))

        // ---------
        // Test 8.
        // ---------
        // If we try to connect same process (=chain) again, nothing happens (only get a bunch o warnings)
        val noPipes = controller.maybeConnect(mockProcessChain6, dummyHeight)
        assertEquals(0, noPipes.size)

        // ---------
        // Test 8.a Receiver
        // ---------
        // If we ask the Receiver for the listener's pipes we only get one pipe
        val pipesForChain1_new = controller.icmfReceiver.getAllPipesForListenerChain(chain1.chainId!!)
        assertEquals(4, pipesForChain1_new.size)
        assertTrue(hasPipeId( "3-1",  pipesForChain1_new))
        assertTrue(hasPipeId( "4-1",  pipesForChain1_new))
        assertTrue(hasPipeId( "5-1",  pipesForChain1_new))
        assertTrue(hasPipeId( "6-1",  pipesForChain1_new))

        // ---------
        // Test 8.b Dispatcher
        // ---------
        // If we ask the Dispatcher about the source we should see it too
        val pipesDispatch = controller.icmfDispatcher.getAllPipesForSourceChain(6)
        assertEquals(3, pipesDispatch.size)
        assertTrue(hasPipeId( "6-1",  pipesDispatch))
        assertTrue(hasPipeId( "6-2",  pipesDispatch))
        assertTrue(hasPipeId( "6-3",  pipesDispatch))
    }


    /**
     * This will mock the entire BC process and the config.
     * Have to mock this, sorry, knew this would be hard to test :-)
     *
     * @param chainIid is the chain we pretend this process is for.
     */
    private fun mockBlockchainProcess(chainIid: Long, listenerConfig: String?): BlockchainProcess {
        val bcRid = BlockchainRid.buildRepeat(chainIid.toByte())

        val mockConfig: BlockchainConfiguration = mock {
            on { chainID }.thenReturn(chainIid)
            on { blockchainRid }.thenReturn(bcRid)
            on { icmfListener }.thenReturn(listenerConfig)
        }
        val mockEngine: BlockchainEngine = mock {
            on { getConfiguration() }.thenReturn(mockConfig)
        }
        val mockProcess: BlockchainProcess = mock {
            on { blockchainEngine }.thenReturn(mockEngine)
        }

        return mockProcess
    }

    private fun hasPipeId(lookForPipeId: String, pipes: List<IcmfPipe>): Boolean =
        pipes.first { lookForPipeId == it.pipeId } != null
}
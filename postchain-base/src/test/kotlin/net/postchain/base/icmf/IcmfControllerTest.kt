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

class IcmfControllerTest : EasyMockSupport() {

    /**
     * Set up some fake listener chains and try to connect a source chain.
     * Should give us same amount of pipes as number of listener chains.
     */
    @Test
    fun happyTest() {
        // Setup PipeConnSync
        val connSync = IcmfPipeConnectionSync(SimpleConfReaderMock("all")) // Using the mock to avoid the DB.
        val chains = setOf(
            BlockchainRelatedInfo(BlockchainRid.buildRepeat(1), null, 1L),
            BlockchainRelatedInfo(BlockchainRid.buildRepeat(2), null, 2L),
            BlockchainRelatedInfo(BlockchainRid.buildRepeat(3), null, 3L)
            )
        connSync.addChains(chains) // Using the SimpleConfReader, these will all look like listener chains
        val chain4 = BlockchainRelatedInfo(BlockchainRid.buildRepeat(4), null, 4L)
        val map = connSync.getListeningChainsForSource(chain4) // Chain 4 won't be seen as a listener.
        val allChainRids = map.keys
        assertEquals(3, allChainRids.size, "A new source should get connected to all existing listeners")

        // Setup Controller
        val controller = IcmfController()
        controller.initialize(connSync)
        val allChains = chains.filter { it.blockchainRid in allChainRids }.toMutableSet()

        // Let's add one extra listener chain to see that it works
        val chain5 = BlockchainRelatedInfo(BlockchainRid.buildRepeat(5), null, 5L)
        allChains.add(chain5)
        controller.setAllChains(allChains) // Chain 5 will become a listener chain, b/c of our dummy SimpleConfReaderMock.

        // Have to mock this, sorry, knew this would be hard to test :-)
        val mockProcess: BlockchainProcess = mock(BlockchainProcess::class.java)
        val mockEngine: BlockchainEngine = mock(BlockchainEngine::class.java)
        val mockConfig: BlockchainConfiguration = mock(BlockchainConfiguration::class.java)
        EasyMock.expect(mockProcess.getEngine()).andReturn(mockEngine).anyTimes()
        EasyMock.expect(mockEngine.getConfiguration()).andReturn(mockConfig).anyTimes()
        EasyMock.expect(mockConfig.chainID).andReturn(6L).anyTimes()
        EasyMock.expect(mockConfig.blockchainRid).andReturn(BlockchainRid.buildRepeat(6)).anyTimes()
        replayAll()

        // Finally: call maybe connect
        val newPipes = controller.maybeConnect(mockProcess)
        assertEquals(4, newPipes.size)
        // What order the pipes are sorted in doesn't matter to us, but I'm lazy here b/c I know the order
        assertTrue(hasPipeId( "6-1",  newPipes))
        assertTrue(hasPipeId( "6-2",  newPipes))
        assertTrue(hasPipeId( "6-3",  newPipes))
        assertTrue(hasPipeId( "6-5",  newPipes))

        // If we try to connect same process (=chain) again, nothing happens (only get a bunch o warnings)
        val noPipes = controller.maybeConnect(mockProcess)
        assertEquals(0, noPipes.size)

    }

    private fun hasPipeId(lookForPipeId: String, pipes: List<IcmfPipe>): Boolean =
        pipes.first { lookForPipeId == it.pipeId } != null
}
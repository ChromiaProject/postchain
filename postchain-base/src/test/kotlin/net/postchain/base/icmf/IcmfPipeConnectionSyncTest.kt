package net.postchain.base.icmf

import net.postchain.base.BlockchainRelatedInfo
import net.postchain.core.BlockchainRid
import org.junit.Test
import kotlin.test.assertEquals

class IcmfPipeConnectionSyncTest {

    @Test
    fun happyTest() {

        val connSync = IcmfPipeConnectionSync()
        val chains = arrayOf(
            BlockchainRelatedInfo(BlockchainRid.buildRepeat(1), null, 1L),
            BlockchainRelatedInfo(BlockchainRid.buildRepeat(2), null, 2L),
            BlockchainRelatedInfo(BlockchainRid.buildRepeat(3), null, 3L)
        )
        // Phase 1: Add listener chains
        val ret1 = connSync.getSourceChainsFromListener(chains[0].chainId!!)
        assertEquals(0, ret1.size) // Fetching potential source chains means everything, but since this is the first chain we get nothing
        connSync.addListenerChain(chains[0].chainId!!, LevelConnectionChecker(chains[0].chainId!!, 10))

        val ret2 = connSync.getSourceChainsFromListener(chains[1].chainId!!)
        assertEquals(1, ret2.size)
        connSync.addListenerChain(chains[1].chainId!!, LevelConnectionChecker(chains[1].chainId!!, 10))

        val ret3 = connSync.getSourceChainsFromListener(chains[2].chainId!!)
        assertEquals(2, ret3.size)
        connSync.addListenerChain(chains[2].chainId!!, LevelConnectionChecker(chains[2].chainId!!, 5))

        // Phase 2: Add a source chain
        val chain4 = BlockchainRelatedInfo(BlockchainRid.buildRepeat(4), null, 4L)
        val ret4 = connSync.getListeningChainsFromSource(chain4.chainId!!)
        assertEquals(3, ret4.size)

        // Phase 3: Shutdown a chain
        connSync.chainShuttingDown(1L)

        // Phase 4: Check if listener is gone
        val ret4_new = connSync.getListeningChainsFromSource(chain4.chainId!!)
        assertEquals(2, ret4_new.size)

    }


}
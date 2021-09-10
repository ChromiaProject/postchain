package net.postchain.base.icmf

import net.postchain.base.BlockchainRelatedInfo
import net.postchain.config.blockchain.SimpleConfReaderMock
import net.postchain.core.BlockchainRid
import org.junit.Test
import kotlin.test.assertEquals

class IcmfPipeConnectionSyncTest {

    @Test
    fun happyTest() {

        val connSync = IcmfPipeConnectionSync(SimpleConfReaderMock("all")) // Using the mock to avoid the DB.
        val chains = setOf(
            BlockchainRelatedInfo(BlockchainRid.buildRepeat(1), null, 1L),
            BlockchainRelatedInfo(BlockchainRid.buildRepeat(2), null, 2L),
            BlockchainRelatedInfo(BlockchainRid.buildRepeat(3), null, 3L)
        )
        connSync.addChains(chains) // Using the SimpleConfReader, these will all look like listener chains
        val chain4 = BlockchainRelatedInfo(BlockchainRid.buildRepeat(4), null, 4L)
        val map = connSync.getListeningChainsForSource(chain4)

        assertEquals(3, map.size)
    }


}
package net.postchain.api.rest.controller

import net.postchain.base.BaseBlockQueries
import net.postchain.common.BlockchainRid
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.EContext
import net.postchain.core.Storage
import net.postchain.core.TransactionFactory
import net.postchain.core.TransactionQueue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class PostchainModelTest {

    private val chainIID = 42L
    private val historicConfigHeight = 54L

    private lateinit var model: PostchainModel
    private lateinit var txQueue: TransactionQueue
    private lateinit var txFactory: TransactionFactory
    private lateinit var baseBlockQueries: BaseBlockQueries
    private lateinit var debugInfoQuery: DebugInfoQuery
    private lateinit var blockchainConfigurationProvider: BlockchainConfigurationProvider
    private lateinit var storage: Storage
    private lateinit var blockchainRID: BlockchainRid
    private lateinit var ctx: EContext

    @BeforeEach
    fun setup() {
        txQueue = mock()
        txFactory = mock()
        baseBlockQueries = mock()
        debugInfoQuery = mock()
        blockchainConfigurationProvider = mock {
            on { getHistoricConfigurationHeight(any(), any(), any()) } doReturn historicConfigHeight
        }
        ctx = mock()
        storage = mock {
            on { openReadConnection(chainIID) } doReturn ctx
        }
        blockchainRID = mock {
            on { toHex() } doReturn ""
        }

        model = PostchainModel(
                chainIID,
                txQueue,
                txFactory,
                baseBlockQueries,
                debugInfoQuery,
                blockchainRID,
                blockchainConfigurationProvider,
                storage
        )
    }

    @Test
    fun `getBlockchainConfiguration with height -1 should get active configuration`() {
        val height = -1L
        model.getBlockchainConfiguration(height)
        verify(blockchainConfigurationProvider).getActiveBlocksConfiguration(ctx, chainIID)
    }

    @Test
    fun `getBlockchainConfiguration with positive height should get active configuration at height`() {
        val height = 42L
        model.getBlockchainConfiguration(height)
        verify(blockchainConfigurationProvider).getHistoricConfigurationHeight(ctx, chainIID, height)
        verify(blockchainConfigurationProvider).getHistoricConfiguration(ctx, chainIID, historicConfigHeight)
    }
}
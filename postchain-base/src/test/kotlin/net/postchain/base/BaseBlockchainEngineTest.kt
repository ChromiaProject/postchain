package net.postchain.base

import net.postchain.base.data.BaseManagedBlockBuilder
import net.postchain.base.data.BaseTransactionQueue
import net.postchain.base.data.DatabaseAccess
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.AfterCommitHandler
import net.postchain.core.BeforeCommitHandler
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainRestartNotifier
import net.postchain.core.EContext
import net.postchain.core.Storage
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.core.block.BlockQueries
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.metrics.BaseBlockchainEngineMetrics
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.sql.Savepoint

class BaseBlockchainEngineTest {

    private val chainId: Long = 42
    private var nanoTime = 54L
    private val useParallelDecoding: Boolean = true
    private val beforeCommitHandler: BeforeCommitHandler = { _, _ -> }
    private val afterCommitHandler: AfterCommitHandler = { _, _, _ -> true }

    private val blockBuilder: BaseManagedBlockBuilder = mock()
    private val blockchainConfiguration: BlockchainConfiguration = mock {
        on { chainID } doReturn chainId
        on { makeBlockBuilder(isA(), anyBoolean(), isA()) } doReturn blockBuilder
    }
    private val db: DatabaseAccess = mock()
    private val savepoint: Savepoint = mock()
    private val conn: Connection = mock {
        on { isClosed } doReturn false
        on { setSavepoint(anyString()) } doReturn savepoint
    }
    private val eContext: EContext = BaseEContext(conn, chainId, db)
    private val blockBuilderStorage: Storage = mock {
        on { openReadConnection(chainId) } doReturn eContext
    }
    private val sharedStorage: Storage = mock()
    private val blockchainConfigurationProvider: BlockchainConfigurationProvider = mock()
    private val restartNotifier: BlockchainRestartNotifier = mock()
    private val nodeDiagnosticContext: NodeDiagnosticContext = mock()
    private val blockQueries: BlockQueries = mock()
    private val transactionQueue: BaseTransactionQueue = mock()
    private val metrics: BaseBlockchainEngineMetrics = mock()
    private val strategy: BlockBuildingStrategy = mock()

    private lateinit var sut: BaseBlockchainEngine

    @BeforeEach
    fun beforeEach() {
        sut = BaseBlockchainEngine(blockchainConfiguration, blockBuilderStorage, sharedStorage, chainId, eContext,
                blockchainConfigurationProvider, restartNotifier, nodeDiagnosticContext, beforeCommitHandler,
                afterCommitHandler, useParallelDecoding, blockQueries, transactionQueue, metrics, strategy,
                { _, _, _, _, _, _ -> blockBuilder }, { nanoTime })
        doReturn(false).whenever(db).configurationHashExists(isA(), isA())
    }

    @Test
    fun `BuildBlock with forced stop should do rollback`() {
        // setup
        doReturn(true).whenever(strategy).shouldForceStopBlockBuilding()
        doNothing().whenever(blockBuilder).rollback()
        // execute
        sut.buildBlock()
        // verify
        verify(blockBuilder).rollback()
        verify(nodeDiagnosticContext, never()).blockchainErrorQueue(isA())
    }
}
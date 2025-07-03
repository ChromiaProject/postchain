package net.postchain.base

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.base.SpecialTransactionPosition.Begin
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.base.data.BaseManagedBlockBuilder
import net.postchain.base.data.BaseTransactionQueue
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DisabledAsyncQueryQueue
import net.postchain.base.data.cryptoSystem
import net.postchain.common.BlockchainRid
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.AfterCommitHandler
import net.postchain.core.BeforeCommitHandler
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainRestartNotifier
import net.postchain.core.EContext
import net.postchain.core.Storage
import net.postchain.core.Transaction
import net.postchain.core.TxEContext
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.BlockStore
import net.postchain.core.block.InitialBlockData
import net.postchain.debug.DiagnosticQueue
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtv.merkleHash
import net.postchain.metrics.BaseBlockchainEngineMetrics
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
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
        on { blockchainRid } doReturn BlockchainRid.ZERO_RID
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
        on { claimSharedContext(eContext) } doReturn eContext
    }
    private val sharedStorage: Storage = mock()
    private val blockchainConfigurationProvider: BlockchainConfigurationProvider = mock()
    private val restartNotifier: BlockchainRestartNotifier = mock()
    private val diagnosticQueue: DiagnosticQueue = mock()
    private val nodeDiagnosticContext: NodeDiagnosticContext = mock {
        on { blockchainBlockStats(org.mockito.kotlin.any()) } doReturn diagnosticQueue
        on { blockchainErrorQueue(org.mockito.kotlin.any()) } doReturn diagnosticQueue
    }
    private val blockQueries: BlockQueries = mock()
    private val transactionQueue: BaseTransactionQueue = mock()
    private var metrics: BaseBlockchainEngineMetrics = mock()
    private val strategy: BlockBuildingStrategy = mock()

    lateinit var specialTransactionHandler: SpecialTransactionHandler
    lateinit var blockStore: BlockStore
    lateinit var baseBlockBuilder: BaseBlockBuilder
    private lateinit var sut: BaseBlockchainEngine

    private val merkleHashCalculator = GtvMerkleHashCalculatorV2(cryptoSystem)
    private val specialTx: Transaction = mock {
        on { isSpecial() } doReturn true
        on { getRawData() } doReturn ByteArray(0)
        on { getRID() } doReturn ByteArray(0)
        on { apply(any()) } doReturn true
        on { getHash() } doReturn gtv(0).merkleHash(merkleHashCalculator)
    }
    private val normalTx: Transaction = mock {
        on { isSpecial() } doReturn false
        on { getRawData() } doReturn ByteArray(0)
        on { getRID() } doReturn ByteArray(0)
        on { apply(any()) } doReturn true
        on { getHash() } doReturn gtv(1).merkleHash(merkleHashCalculator)
    }

    @BeforeEach
    fun beforeEach() {
        metrics = BaseBlockchainEngineMetrics(
                0, BlockchainRid.ZERO_RID, transactionQueue
        )
        specialTransactionHandler = mock<SpecialTransactionHandler>()
        blockStore = mock<BlockStore>() {
            on { beginBlock(any(), any(), anyOrNull()) } doReturn InitialBlockData(BlockchainRid.ZERO_RID, 0, 0, ByteArray(0), 0, 0, null)
            on { addTransaction(any(), any(), any()) } doReturn(mock<TxEContext>())
        }
        sut = BaseBlockchainEngine(blockchainConfiguration, blockBuilderStorage, sharedStorage, chainId, eContext,
                blockchainConfigurationProvider, restartNotifier, nodeDiagnosticContext, beforeCommitHandler,
                afterCommitHandler, useParallelDecoding, blockQueries, transactionQueue, metrics, strategy, DisabledAsyncQueryQueue(),
                { _, _, _, _, _, _ -> blockBuilder }, { nanoTime }, )
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

    @ParameterizedTest
    @ValueSource(ints = [1, 2])
    fun `BuildBlock stops add txs when max 1 is reached with no special tx`(maxBlockTransactions: Int) {
        // setup
        baseBlockBuilder = buildBaseBlockBuilder(maxBlockTransactions.toLong())

        // execute
        sut.buildBlock()

        // verify
        verify(nodeDiagnosticContext, never()).blockchainErrorQueue(isA())
        verify(blockBuilder).finalizeBlock()
        verify(blockStore, times(maxBlockTransactions)).addTransaction(any(), any(), any())
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2])
    fun `BuildBlock stops add txs when max is reached with begin special tx`(maxBlockTransactions: Int) {
        // setup
        baseBlockBuilder = buildBaseBlockBuilder(maxBlockTransactions.toLong())
        whenever(specialTransactionHandler.needsSpecialTransaction(eq(Begin))).thenReturn(true)
        whenever(specialTransactionHandler.createSpecialTransaction(eq(Begin), any())).thenReturn(specialTx)
        whenever(transactionQueue.takeTransaction(any()))
                .thenReturn(normalTx)
                .thenAnswer { throw RuntimeException("No more transactions should have been requested") }

        // execute
        sut.buildBlock()

        // verify
        verify(nodeDiagnosticContext, never()).blockchainErrorQueue(isA())
        verify(blockBuilder).finalizeBlock()
        argumentCaptor<Transaction> {
            verify(blockStore, times(maxBlockTransactions)).addTransaction(any(), capture(), any())
            assertThat(allValues.isNotEmpty()).isTrue()
            // First tx is always special
            assertThat(firstValue.isSpecial()).isTrue()
            // The rest is normal
            allValues.drop(1).forEach {
                assertThat(it.isSpecial()).isFalse()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2])
    fun `BuildBlock stops add txs when max is reached with end special tx`(maxBlockTransactions: Int) {
        // setup
        baseBlockBuilder = buildBaseBlockBuilder(maxBlockTransactions.toLong())
        whenever(specialTransactionHandler.needsSpecialTransaction(eq(SpecialTransactionPosition.End))).thenReturn(true)
        whenever(specialTransactionHandler.createSpecialTransaction(eq(SpecialTransactionPosition.End), any())).thenReturn(specialTx)
        whenever(transactionQueue.takeTransaction(any()))
                .thenReturn(normalTx)
                .thenAnswer { throw RuntimeException("No more transactions should have been requested") }

        // execute
        sut.buildBlock()

        // verify
        verify(nodeDiagnosticContext, never()).blockchainErrorQueue(isA())
        verify(blockBuilder).finalizeBlock()
        argumentCaptor<Transaction> {
            verify(blockStore, times(maxBlockTransactions)).addTransaction(any(), capture(), any())
            assertThat(allValues.isNotEmpty()).isTrue()
            // all except last is normal
            allValues.subList(0, allValues.size - 1) .forEach {
                assertThat(it.isSpecial()).isFalse()
            }
            // Last tx is always special
            assertThat(lastValue.isSpecial()).isTrue()
        }
    }

    // Create a BaseBlockBuilder and make the mock blockBuilder delegate to this instance
    private fun buildBaseBlockBuilder(maxBlockTransactions: Long): BaseBlockBuilder {
        val baseBlockBuilder = BaseBlockBuilder(
                BlockchainRid.ZERO_RID,
                cryptoSystem,
                eContext,
                blockStore,
                specialTransactionHandler,
                arrayOf(),
                mock(),
                mock(),
                listOf(),
                listOf(),
                false,
                Long.MAX_VALUE,
                maxBlockTransactions,
                0,
                true,
                Long.MAX_VALUE,
                ByteArray(0),
                false,
                merkleHashCalculator
        )
        whenever(blockBuilder.begin(anyOrNull())).doAnswer {
            baseBlockBuilder.begin(it.getArgument(0))
        }
        whenever(blockBuilder.maybeAppendTransaction(any())).doAnswer {
            baseBlockBuilder.appendTransaction(it.getArgument(0) as Transaction)
            null
        }
        whenever(blockBuilder.finalizeBlock()).thenAnswer {
            baseBlockBuilder.finalizeBlock()
        }
        whenever(transactionQueue.takeTransaction(any())).thenReturn(normalTx)
        whenever(strategy.shouldStopBuildingBlock(anyOrNull())).thenAnswer {
            baseBlockBuilder.shouldStopBuildingBlock(baseBlockBuilder.maxBlockTransactions)
        }
        return baseBlockBuilder
    }
}
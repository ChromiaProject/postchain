package net.postchain.integrationtest.state

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.core.BlockchainState
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.currentHeight
import net.postchain.devtools.mminfra.TestManagedBlockchainProcessManager
import net.postchain.devtools.utils.ChainUtil
import net.postchain.ebft.worker.ReadOnlyBlockchainProcess
import net.postchain.ebft.worker.ValidatorBlockchainProcess
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ColumnListHandler
import org.awaitility.Awaitility.await
import org.awaitility.Duration
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

class BlockchainStateTest : ManagedModeTest() {

    @Test
    fun `Test switching blockchain state`() {
        startManagedSystem(3, 0)
        val c1 = startNewBlockchain(setOf(0, 1, 2), setOf(), null)
        buildBlock(c1, 1)

        verifyState(c1, BlockchainState.RUNNING, ValidatorBlockchainProcess::class)

        setBlockchainState(c1, BlockchainState.PAUSED)
        verifyState(c1, BlockchainState.PAUSED, ReadOnlyBlockchainProcess::class)

        setBlockchainState(c1, BlockchainState.RUNNING)
        verifyState(c1, BlockchainState.RUNNING, ValidatorBlockchainProcess::class)

        buildBlock(c1)
    }

    @Test
    fun `Test deleting and archiving blockchain`() {
        // Setup
        configOverrides.setProperty("housekeeping_interval_ms", 5_000)
        startManagedSystem(3, 0)
        val c1 = startNewBlockchain(setOf(0, 1, 2), setOf(), null)
        buildBlock(c1, 1)
        val c2 = startNewBlockchain(setOf(0, 1, 2), setOf(), null)
        buildBlock(c2, 1)

        // Asserting the initial state
        verifyState(c1, BlockchainState.RUNNING, ValidatorBlockchainProcess::class)
        verifyState(c2, BlockchainState.RUNNING, ValidatorBlockchainProcess::class)
        await().atMost(Duration.ONE_MINUTE).untilAsserted {
            getChainNodes(c0).forEach {
                assertThat(it.retrieveBlockchain(c1)).isNotNull()
                assertThat(it.retrieveBlockchain(c2)).isNotNull()
                assertThat((it.processManager as TestManagedBlockchainProcessManager).getCurInactiveBcHeight())
                        .isEqualTo(0L)
            }
        }

        // 1. Removing c1 at a specific height
        val heightC1RemovedAt = nodes.first().currentHeight(c0) + 1
        setBlockchainState(c1, BlockchainState.REMOVED, heightC1RemovedAt)

        // 2. Archiving c2 at a specific height
        val heightC2ArchivedAt = nodes.first().currentHeight(c0) + 1
        setBlockchainState(c2, BlockchainState.ARCHIVED, heightC2ArchivedAt)

        // Asserting that c1 is removed
        await().atMost(Duration.ONE_MINUTE).untilAsserted {
            getChainNodes(c1).forEach {
                assertThat(it.retrieveBlockchain(c1)).isNull()
                val chainId1FromDb = withReadConnection(nodes.first().postchainContext.sharedStorage, c0) { ctx0 ->
                    DatabaseAccess.of(ctx0).getChainId(ctx0, ChainUtil.ridOf(c1))
                }
                assertThat(chainId1FromDb).isNull()
            }
        }
        // Asserting that `ManagedBlockchainProcessManager.currentInactiveBlockchainsHeight` equals height blockchain was removed at
        assertThat((nodes.first().processManager as TestManagedBlockchainProcessManager).getCurInactiveBcHeight())
                .isEqualTo(heightC1RemovedAt)

        // Asserting that c2 is archived
        val queryRunner = QueryRunner()
        val selectBcTables = "SELECT tables.table_name FROM information_schema.tables AS tables" +
                " WHERE tables.table_schema = current_schema() AND tables.table_name LIKE 'c$c2.%'"
        await().atMost(Duration.ONE_MINUTE).untilAsserted {
            getChainNodes(c2).forEach {
                assertThat(it.retrieveBlockchain(c2)).isNull()
                val tables = withReadConnection(nodes.first().postchainContext.sharedStorage, c0) { ctx0 ->
                    queryRunner.query(ctx0.conn, selectBcTables, ColumnListHandler<String>()).toSet()
                }
                assertThat(tables).isEqualTo(setOf("c$c2.configurations", "c$c2.blocks", "c$c2.transactions"))
            }
        }
        // Asserting that `ManagedBlockchainProcessManager.currentInactiveBlockchainsHeight` equals height blockchain was removed at
        assertThat((nodes.first().processManager as TestManagedBlockchainProcessManager).getCurInactiveBcHeight())
                .isEqualTo(heightC2ArchivedAt)

        // 3. Removing archived c2 at a specific height
        val heightC2RemovedAt = nodes.first().currentHeight(c0) + 1
        setBlockchainState(c2, BlockchainState.REMOVED, heightC2RemovedAt)

        // Asserting that c2 is removed
        await().atMost(Duration.ONE_MINUTE).untilAsserted {
            getChainNodes(c2).forEach {
                assertThat(it.retrieveBlockchain(c2)).isNull()
                val chainId2FromDb = withReadConnection(nodes.first().postchainContext.sharedStorage, c0) { ctx0 ->
                    DatabaseAccess.of(ctx0).getChainId(ctx0, ChainUtil.ridOf(c2))
                }
                assertThat(chainId2FromDb).isNull()
            }
        }
        // Asserting that `ManagedBlockchainProcessManager.currentInactiveBlockchainsHeight` equals height blockchain was removed at
        assertThat((nodes.first().processManager as TestManagedBlockchainProcessManager).getCurInactiveBcHeight())
                .isEqualTo(heightC2RemovedAt)
    }

    private fun <T : Any> verifyState(chainId: Long, state: BlockchainState, clazz: KClass<in T>) {
        await().atMost(Duration.ONE_MINUTE).untilAsserted {
            getChainNodes(chainId).forEach {
                val chain = it.retrieveBlockchain(chainId)
                assertThat(chain).isNotNull()
                assertThat(chain!!.getBlockchainState()).isEqualTo(state)
                assertThat(chain).isInstanceOf(clazz.java)
            }
        }
    }

    private fun basicSystem(): Long {
        startManagedSystem(3, 0)
        val c1 = startNewBlockchain(setOf(0, 1, 2), setOf(), null)
        buildBlock(c1, 1)
        return c1
    }
}
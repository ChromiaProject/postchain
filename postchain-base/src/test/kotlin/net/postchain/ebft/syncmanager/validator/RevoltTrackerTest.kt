package net.postchain.ebft.syncmanager.validator

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.core.BlockchainEngine
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.ebft.NodeStatus
import net.postchain.ebft.StatusManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RevoltTrackerTest {

    private val myStatus: NodeStatus = NodeStatus(0, 0)
    private val statusManager: StatusManager = mock {
        on { myStatus } doReturn myStatus
    }
    private val blockBuildingStrategy: BlockBuildingStrategy = mock {
        on { shouldBuildBlock() } doReturn false
    }
    private val engine: BlockchainEngine = mock {
        on { getBlockBuildingStrategy() } doReturn blockBuildingStrategy
    }
    private val revoltConfig: RevoltConfigurationData = mock {
        on { timeout } doReturn 1000
        on { exponentialDelayBase } doReturn 1000
        on { exponentialDelayMax } doReturn 600_000
        on { fastRevoltStatusTimeout } doReturn -1
    }

    private var currentTimeMillis: Long = 100
    private lateinit var sut: RevoltTracker

    companion object {
        @JvmStatic
        fun getDeadlineTestData(): List<Array<Any>> {
            return listOf(
                    arrayOf(2, 1540),
                    arrayOf(10, 6291),
                    arrayOf(34, 492323),
                    arrayOf(35, 601100), // Max round
                    arrayOf(36, 601100),
                    arrayOf(40, 601100),
            )
        }
    }

    @BeforeEach
    fun setUp() {
        sut = object : RevoltTracker(statusManager, revoltConfig, engine) {
            override fun currentTimeMillis() = currentTimeMillis
        }
    }

    @Test
    fun `Fast revolt should revolt if too long time has gone since last update`() {
        // setup
        myStatus.height = 1
        whenever(revoltConfig.fastRevoltStatusTimeout).thenReturn(10)
        whenever(statusManager.primaryIndex()).thenReturn(42)
        whenever(statusManager.getLatestStatusTimestamp(42)).thenReturn(54)
        // execute
        sut.update()
        // verify
        verify(statusManager).onStartRevolting()
    }

    @Test
    fun `Fast revolt should not revolt if too long time has gone since last update but revolt is ongoing`() {
        // setup
        myStatus.height = 1
        myStatus.revolting = true
        whenever(revoltConfig.fastRevoltStatusTimeout).thenReturn(10)
        whenever(statusManager.primaryIndex()).thenReturn(42)
        whenever(statusManager.getLatestStatusTimestamp(42)).thenReturn(54)
        // execute
        sut.update()
        // verify
        verify(statusManager, never()).onStartRevolting()
    }

    @Test
    fun `Fast revolt should not revolt if too short time has gone since last update`() {
        // setup
        myStatus.height = 1
        whenever(revoltConfig.fastRevoltStatusTimeout).thenReturn(10)
        whenever(statusManager.primaryIndex()).thenReturn(42)
        whenever(statusManager.getLatestStatusTimestamp(42)).thenReturn(95)
        // execute
        sut.update()
        // verify
        verify(statusManager, never()).onStartRevolting()
    }

    @Test
    fun `Should not fast revolt if fastRevoltStatusTimeout is not set`() {
        // execute
        sut.update()
        // verify
        verify(statusManager, never()).onStartRevolting()
    }

    @Test
    fun `Should not fast revolt if height is the same`() {
        // setup
        whenever(revoltConfig.fastRevoltStatusTimeout).thenReturn(10)
        // execute
        sut.update()
        // verify
        verify(statusManager, never()).onStartRevolting()
    }

    @Test
    fun `Should not fast revolt if my node is primary`() {
        // setup
        myStatus.height = 1
        whenever(revoltConfig.fastRevoltStatusTimeout).thenReturn(10)
        whenever(statusManager.isMyNodePrimary()).thenReturn(true)
        // execute
        sut.update()
        // verify
        verify(statusManager, never()).onStartRevolting()
    }

    @Test
    fun `If no preconditions are met do not start revolt`() {
        // setup
        whenever(blockBuildingStrategy.shouldBuildBlock()).thenReturn(true)
        // execute
        sut.update()
        // verify
        verify(statusManager, never()).onStartRevolting()
    }

    @Test
    fun `If height has changed calculate new deadline`() {
        // setup
        myStatus.height = 1
        myStatus.round = 2
        whenever(blockBuildingStrategy.shouldBuildBlock()).thenReturn(true)
        // execute
        sut.update()
        // verify
        assertThat(sut.deadLine).isEqualTo(1540)
        verify(statusManager, never()).onStartRevolting()
    }


    @ParameterizedTest
    @MethodSource("getDeadlineTestData")
    fun `If round has changed calculate new deadline`(round: Long, deadLine: Long) {
        // setup
        myStatus.round = round
        whenever(blockBuildingStrategy.shouldBuildBlock()).thenReturn(true)
        // execute
        sut.update()
        // verify
        assertThat(sut.deadLine).isEqualTo(deadLine)
        verify(statusManager, never()).onStartRevolting()
    }

    @Test
    fun `If deadline has not passed do not revolt`() {
        // setup
        whenever(blockBuildingStrategy.shouldBuildBlock()).thenReturn(true)
        currentTimeMillis = 1100
        // execute
        sut.update()
        // verify
        verify(statusManager, never()).onStartRevolting()
    }

    @Test
    fun `If deadline has passed and is already revolting, do not revolt again`() {
        // setup
        whenever(blockBuildingStrategy.shouldBuildBlock()).thenReturn(true)
        currentTimeMillis = 10000
        myStatus.revolting = true
        // execute
        sut.update()
        // verify
        verify(statusManager, never()).onStartRevolting()
    }

    @Test
    fun `If deadline has passed do revolt`() {
        // setup
        whenever(blockBuildingStrategy.shouldBuildBlock()).thenReturn(true)
        currentTimeMillis = 10000
        // execute
        sut.update()
        // verify
        verify(statusManager).onStartRevolting()
    }
}
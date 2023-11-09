package net.postchain.core.framework

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainState
import org.awaitility.Duration
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.lang.Thread.sleep
import java.util.concurrent.TimeUnit

internal class AbstractBlockchainProcessTest {

    private val configuration: BlockchainConfiguration = mock {
        on { chainID } doReturn 0L
        on { blockchainRid } doReturn BlockchainRid.ZERO_RID
    }

    private val engine: BlockchainEngine = mock {
        on { getConfiguration() } doReturn configuration
    }

    @Test
    fun `Process is started and stopped successfully`() {
        val process = DummyBlockchainProcess({ sleep(1) }, engine)
        assertThat(process.isProcessRunning()).isFalse()
        process.start()
        assertThat(process.isProcessRunning()).isTrue()
        assertThat(process.process.isAlive).isTrue()
        process.shutdown()
        assertThat(process.isProcessRunning()).isFalse()
        assertThat(process.process.isAlive).isFalse()
    }

    @Test
    fun `Action throws should stop process`() {
        val process = DummyBlockchainProcess({ throw IllegalArgumentException("failed!") }, engine).apply { start() }
        await.atMost(Duration(1, TimeUnit.SECONDS)).until { !process.process.isAlive }
        assertThat(process.isProcessRunning()).isFalse()
    }

}

class DummyBlockchainProcess(private val testAction: () -> Unit, engine: BlockchainEngine) : AbstractBlockchainProcess("TestProcess", engine) {

    override fun cleanup() {}
    override fun isSigner(): Boolean = true
    override fun getBlockchainState(): BlockchainState = BlockchainState.RUNNING

    override fun action() {
        testAction()
    }

    override fun currentBlockHeight(): Long = 0L
}

package net.postchain.core.framework

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainState
import org.awaitility.Duration
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.lang.Thread.sleep
import java.util.concurrent.TimeUnit

internal class AbstractBlockchainProcessTest {

    @Test
    fun `Process is started and stopped successfully`() {
        val process = DummyBlockchainProcess { sleep(1) }
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
        val process = DummyBlockchainProcess { throw IllegalArgumentException() }.apply { start() }
        await.atMost(Duration(1, TimeUnit.SECONDS)).until { !process.process.isAlive }
        assertThat(process.isProcessRunning()).isFalse()
    }

}

class DummyBlockchainProcess(private val testAction: () -> Unit) : AbstractBlockchainProcess("TestProcess", mock(BlockchainEngine::class.java)) {

    override fun cleanup() {}
    override fun isSigner(): Boolean = true
    override fun getBlockchainState(): BlockchainState = BlockchainState.RUNNING

    override fun action() {
        testAction()
    }
}

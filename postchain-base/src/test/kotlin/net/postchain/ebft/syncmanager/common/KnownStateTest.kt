package net.postchain.ebft.syncmanager.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.ebft.syncmanager.common.KnownState.State.BLACKLISTED
import net.postchain.ebft.syncmanager.common.KnownState.State.SYNCABLE
import net.postchain.ebft.syncmanager.common.KnownState.State.UNRESPONSIVE
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock

class KnownStateTest {

    private val clock: Clock = mock()
    private val params: SyncParameters = mock()

    private lateinit var sut: KnownState

    @BeforeEach
    fun setup() {
        sut = KnownState(params, clock)
    }

    @Nested
    inner class updateAndCheckBlacklisted {
        @Test
        fun `when not blacklisted should return false`() {
            // setup
            doReturn(100L).whenever(params).blacklistingTimeoutMs
            // execute
            assertThat(sut.updateAndCheckBlacklisted()).isFalse()
        }

        @Test
        fun `when blacklisted should return true`() {
            // setup
            doReturn(100L).whenever(params).blacklistingTimeoutMs
            doReturn(1).whenever(params).maxErrorsBeforeBlacklisting
            sut.blacklist("Fail", 90)
            assertThat(sut.state).isEqualTo(BLACKLISTED)
            // execute
            assertThat(sut.updateAndCheckBlacklisted()).isTrue()
            // verify
            assertThat(sut.state).isEqualTo(BLACKLISTED)
        }

        @Test
        fun `when blacklisted but error time has timed out should return false`() {
            // setup
            doReturn(100L).whenever(params).blacklistingTimeoutMs
            doReturn(1).whenever(params).maxErrorsBeforeBlacklisting
            doReturn(500L).whenever(clock).millis()
            sut.blacklist("Fail", 10)
            assertThat(sut.state).isEqualTo(BLACKLISTED)
            // execute
            assertThat(sut.updateAndCheckBlacklisted()).isFalse()
            // verify
            assertThat(sut.state).isEqualTo(SYNCABLE)
        }
    }


    @Nested
    inner class isUnresponsive {
        @Test
        fun `when unresponsive should return true`() {
            // setup
            doReturn(100L).whenever(params).resurrectUnresponsiveTime
            sut.unresponsive("Chilling", 90)
            assertThat(sut.state).isEqualTo(UNRESPONSIVE)
            // execute
            assertThat(sut.isUnresponsive(10)).isTrue()
            // verify
            assertThat(sut.state).isEqualTo(UNRESPONSIVE)
        }

        @Test
        fun `when unresponsive for long enough should return false`() {
            // setup
            doReturn(100L).whenever(params).resurrectUnresponsiveTime
            sut.unresponsive("Chilling", 90)
            assertThat(sut.state).isEqualTo(UNRESPONSIVE)
            // execute
            assertThat(sut.isUnresponsive(200)).isFalse()
            // verify
            assertThat(sut.state).isEqualTo(SYNCABLE)
        }
    }
}
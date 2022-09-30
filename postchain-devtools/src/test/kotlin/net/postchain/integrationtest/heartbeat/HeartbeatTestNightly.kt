package net.postchain.integrationtest.heartbeat

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import net.postchain.devtools.ManagedModeTest
import org.awaitility.Awaitility.await
import org.awaitility.Duration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timer

@Disabled // ICMF is coming. Heartbeat will be removed soon.
class HeartbeatTestNightly : ManagedModeTest() {

    private val maxBlockTime = 500L

    @BeforeEach
    fun setUp() {
        // Configuring Heartbeat
        configOverrides.setProperty("heartbeat.timeout", 5000)
        configOverrides.setProperty("heartbeat.sleep_timeout", 2000)
    }

    @Test
    fun testHeartbeat_When_Enabled() {
        // Enabling heartbeat
        configOverrides.setProperty("heartbeat.enabled", true)

        // Starting chains
        startManagedSystem(1, 0)
        val c1 = startNewBlockchain(setOf(0), emptySet())

        val bb0 = ManagedBlockBuildingStrategy(this, c0).also { it.start() }
        ManagedBlockBuildingStrategy(this, c1).also { it.start() }

        var height0 = 0L
        var height1 = 0L

        // Reaching height 5 at least
        await().pollInterval(Duration.ONE_SECOND)
                .atMost(Duration.ONE_MINUTE)
                .untilAsserted {
                    height0 = getLastHeight(c0)
                    height1 = getLastHeight(c1)
                    println("c0 height: $height0")
                    println("c1 height: $height1")
                    assert(height0).isGreaterThan(5L)
                    assert(height1).isGreaterThan(5L)
                }

        // Chain0 is getting stuck
        // When chain0 is stopped, chainX has to pause block production not to produce blocks on outdated configuration.
        // (Blockchain of chain0 serves as a timeline for chainX; chain0's blocks contain configs for chainX.)
        bb0.pause()

        // Fixing heights
        await().pollDelay(6000, TimeUnit.MILLISECONDS) // Waiting for max(maxBlockTime, heartbeat.timeout) + delta
                .untilAsserted {
                    height0 = getLastHeight(c0)
                    height1 = getLastHeight(c1)
                    println("after c0 gets stuck: c0 height: $height0")
                    println("after c0 gets stuck: c1 height: $height1")
                    assert(height1).isGreaterThan(6L) // newHeight ~= prevHeight + [(heartbeat.timeout) / maxBlockTime] ~= 9
                }

        // Waiting at least '2 * heartbeat.timeout' and asserting that heights are the same
        var newHeight0 = 0L
        var newHeight1 = 0L
        await().pollDelay(11_000, TimeUnit.MILLISECONDS)
                .atMost(Duration.ONE_MINUTE)
                .untilAsserted {
                    newHeight0 = getLastHeight(c0)
                    newHeight1 = getLastHeight(c1)
                    println("after c0 gets stuck + wait: c0 height: $newHeight0")
                    println("after c0 gets stuck + wait: c1 height: $newHeight1")
                    assert(newHeight0).isEqualTo(height0)
                    assert(newHeight1).isEqualTo(height1)
                }

        // Chain0 resumes
        bb0.resume()

        // Waiting at least 'maxBlocktime (c0) + heartbeat.sleep_timeout + maxBlocktime (c1) + delta'
        // and asserting that both chains are building new blocks
        await().pollDelay(5_000, TimeUnit.MILLISECONDS)
                .atMost(Duration.ONE_MINUTE)
                .untilAsserted {
                    val h0 = getLastHeight(c0)
                    val h1 = getLastHeight(c1)
                    println("after c0 resumed: c0 height: $h0")
                    println("after c0 resumed: c1 height: $h1")
                    assert(h0).isGreaterThan(newHeight0)
                    assert(h1).isGreaterThan(newHeight1)
                }
    }

    @Test
    fun testHeartbeat_When_Disabled() {
        // Disabling heartbeat
        configOverrides.setProperty("heartbeat.enabled", false)

        // Starting chains
        startManagedSystem(1, 0)
        val c1 = startNewBlockchain(setOf(0), emptySet())

        val bb0 = ManagedBlockBuildingStrategy(this, c0).also { it.start() }
        ManagedBlockBuildingStrategy(this, c1).also { it.start() }

        var height0 = 0L
        var height1 = 0L

        // Reaching height 5 at least
        await().pollInterval(Duration.ONE_SECOND)
                .atMost(Duration.ONE_MINUTE)
                .untilAsserted {
                    height0 = getLastHeight(c0)
                    height1 = getLastHeight(c1)
                    println("c0 height: $height0")
                    println("c1 height: $height1")
                    assert(height0).isGreaterThan(5L)
                    assert(height1).isGreaterThan(5L)
                }

        // Chain0 is getting stuck
        // While chain0 is stopped, chainX has not to pause block production b/c 'nodeConfig.enabled == false'.
        // (Blockchain of chain0 serves as a timeline for chainX; chain0' blocks contain configs for chainX.)
        bb0.pause()

        // Fixing heights
        await().pollDelay(6000, TimeUnit.MILLISECONDS) // Waiting for max(maxBlockTime, heartbeat.timeout) + delta
                .untilAsserted {
                    height0 = getLastHeight(c0)
                    height1 = getLastHeight(c1)
                    println("after c0 gets stuck: c0 height: $height0")
                    println("after c0 gets stuck: c1 height: $height1")
                    assert(height1).isGreaterThan(6L) // newHeight ~= prevHeight + [(heartbeat.timeout) / maxBlockTime] ~= 9
                }

        // Waiting at least '2 * heartbeat.timeout' and asserting that heights are the same
        await().pollDelay(11_000, TimeUnit.MILLISECONDS)
                .atMost(Duration.ONE_MINUTE)
                .untilAsserted {
                    val newHeight0 = getLastHeight(c0)
                    val newHeight1 = getLastHeight(c1)
                    println("after c0 gets stuck + wait: c0 height: $newHeight0")
                    println("after c0 gets stuck + wait: c1 height: $newHeight1")
                    assert(newHeight0).isEqualTo(height0)
                    assert(newHeight1).isGreaterThan(height1)
                }
    }

    private fun buildNextBlockNoWait(nodeSet: NodeSet) {
        val height = getLastHeight(nodeSet)
        buildBlockNoWait(nodeSet.nodes(), nodeSet.chain, height + 1L)
    }

    private fun getLastHeight(nodeSet: NodeSet): Long {
        return nodeSet.nodes().first().blockQueries(nodeSet.chain).getBestHeight().get()
    }

    private class ManagedBlockBuildingStrategy(val context: HeartbeatTestNightly, val nodeSet: ManagedModeTest.NodeSet) {

        private var running = true

        fun start() {
            timer("blockbuilder-${nodeSet.chain}", period = context.maxBlockTime) {
                if (running) {
                    context.buildNextBlockNoWait(nodeSet)
                }
            }
        }

        fun pause() {
            running = false
        }

        fun resume() {
            running = true
        }
    }

}
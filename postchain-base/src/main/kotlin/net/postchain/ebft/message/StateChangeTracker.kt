package net.postchain.ebft.message

import io.micrometer.core.instrument.Timer
import mu.KLogging
import net.postchain.common.toHex
import net.postchain.common.types.WrappedByteArray
import net.postchain.common.wrap
import net.postchain.config.app.AppConfig
import net.postchain.core.NodeRid
import net.postchain.ebft.NodeBlockState
import net.postchain.ebft.NodeStatus
import net.postchain.metrics.NodeStatusMetrics
import java.util.concurrent.TimeUnit

class StateChangeTracker(
        appConfig: AppConfig,
        private val nodeStatusMetrics: NodeStatusMetrics,
        private val nanoTime: () -> Long = { System.nanoTime() }
) {
    private val myPubKey = appConfig.pubKey
    private val disabled = appConfig.trackedEbftMessageMaxKeepTimeMs == -1L
    private val maxTrackingTimeMs = appConfig.trackedEbftMessageMaxKeepTimeMs
    private val myStatuses: MutableMap<NodeBlockState, MutableList<TrackedStatus>> = mutableMapOf()
    private val receivedStatuses: MutableMap<NodeBlockState, MutableList<TrackedStatus>> = mutableMapOf()
    private val timers: MutableMap<Pair<String, NodeBlockState>, Timer> = mutableMapOf()
    private val allowedStates = listOf(NodeBlockState.HaveBlock, NodeBlockState.Prepared)

    companion object : KLogging()

    fun statusChange(source: NodeRid, status: NodeStatus) {
        if (disabled || status.state !in allowedStates) return
        val sourcePubKey = source.toHex()
        handleStatusChange(sourcePubKey, status, receivedStatuses, myStatuses) { elapsedTime, it ->
            "Peer $sourcePubKey reached same status ${nodeStatusToString(status)} as $it after $elapsedTime ms"
        }
    }

    fun myStatusChange(status: NodeStatus) {
        if (disabled) return
        handleStatusChange(myPubKey, status, myStatuses, receivedStatuses) { elapsedTime, it ->
            "Reached same status ${nodeStatusToString(status)} as $it after $elapsedTime ms"
        }
    }

    private fun handleStatusChange(
            sourcePubKey: String,
            status: NodeStatus,
            statuses: MutableMap<NodeBlockState, MutableList<TrackedStatus>>,
            comparatorStatuses: MutableMap<NodeBlockState, MutableList<TrackedStatus>>,
            logMessage: (elapsedTime: Long, it: TrackedStatus) -> String
    ) {
        val now = nanoTime()
        cleanup(now)
        val statusList = statuses.getOrPut(status.state) { mutableListOf() }
        val isMissing = statusList.find { it.source == sourcePubKey && it.equalStatus(status) } == null
        if (isMissing) {
            statusList.add(TrackedStatus(sourcePubKey, status, now))
            comparatorStatuses.getOrPut(status.state) { mutableListOf() }
                    .filter { it.equalStatus(status) }
                    .forEach {
                        val timer = timers.getOrPut(sourcePubKey to status.state) {
                            nodeStatusMetrics.createStatusChangeTimeTimer(it.source, sourcePubKey, status.state)
                        }
                        val elapsedTime = getElapsedTimeMs(now, it)
                        timer.record(elapsedTime, TimeUnit.MILLISECONDS)
                        logger.trace { logMessage(elapsedTime, it) }
                    }
        }
    }

    private fun cleanup(now: Long) {
        myStatuses.values.forEach { list ->
            list.removeIf {
                getElapsedTimeMs(now, it) >= maxTrackingTimeMs
            }
        }
        receivedStatuses.values.forEach { list ->
            list.removeIf {
                getElapsedTimeMs(now, it) >= maxTrackingTimeMs
            }
        }
    }

    private fun getElapsedTimeMs(now: Long, trackedStatus: TrackedStatus): Long = TimeUnit.NANOSECONDS.toMillis(now - trackedStatus.time)

    private fun nodeStatusToString(status: NodeStatus) =
            "(state=${status.state.name}, serial=${status.serial}, height=${status.height}, round=${status.round}, blockRID=${status.blockRID?.toHex()}, revolting=${status.revolting})"

    private data class TrackedStatus(val source: String, val state: NodeBlockState, val height: Long, val round: Long, val blockRID: WrappedByteArray, val time: Long) {
        constructor(source: String, status: NodeStatus, time: Long) : this(source, status.state, status.height, status.round, status.blockRID!!.wrap(), time)

        fun equalStatus(other: NodeStatus): Boolean =
                this.height == other.height && this.round == other.round && this.blockRID.data.contentEquals(other.blockRID)
    }
}
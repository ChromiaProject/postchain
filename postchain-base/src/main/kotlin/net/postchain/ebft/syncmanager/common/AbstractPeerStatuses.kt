package net.postchain.ebft.syncmanager.common

import mu.KLogging
import net.postchain.core.NodeRid
import net.postchain.devtools.NameHelper

/**
 * Keeps track of peer's statuses. The currently tracked statuses are
 *
 * - Blacklisted: We have received invalid data from the peer, or it's otherwise misbehaving
 * - Unresponsive: We haven't received a timely response from the peer
 * - Responsive: Node should work well
 */
abstract class AbstractPeerStatuses<StateType : KnownState> {

    companion object : KLogging()

    protected val statuses = HashMap<NodeRid, StateType>()

    val peersStates: List<Pair<String, KnownState>>
        get() = statuses.toList().map { it.first.toHex() to it.second }

    abstract fun stateOf(peerId: NodeRid): StateType

    private fun resurrectPeers(now: Long) {
        statuses.forEach {
            it.value.resurrect(now)
        }
    }

    /**
     * @param height is the height we need
     * @param now is our current time (we want to send it to keep this pure = testable)
     * @return the nodes we SHOULDN'T sync
     */
    fun excludedNonSyncable(height: Long, now: Long): Set<NodeRid> {
        resurrectPeers(now)
        val excluded = statuses.filter {
            val state = it.value
            val syncable = state.isSyncable(height) && !state.isMaybeLegacy()
            !syncable
        }
        return excluded.keys

    }

    fun getLegacyPeers(height: Long): Set<NodeRid> {
        return statuses.filterValues { it.isMaybeLegacy() && it.isSyncable(height) }.keys
    }

    /**
     * @param peerId is the peer that's not responding
     * @param desc is the text we will log, surrounding the circumstances.
     *             (This could be caused by a bug, if so it has to be traced)
     */
    fun unresponsive(peerId: NodeRid, desc: String) {
        val status = stateOf(peerId)
        if (status.updateAndCheckBlacklisted()) {
            return
        }
        status.unresponsive(desc, System.currentTimeMillis())
    }

    fun setMaybeLegacy(peerId: NodeRid, isLegacy: Boolean) {
        val status = stateOf(peerId)
        if (status.updateAndCheckBlacklisted()) {
            return
        }
        if (logger.isDebugEnabled) {
            if (status.isMaybeLegacy() != isLegacy) {
                logger.debug("Setting new fast sync peer: ${NameHelper.peerName(peerId)} status maybe legacy: $isLegacy.")
            }
        }
        status.maybeLegacy(isLegacy)
    }

    fun isMaybeLegacy(peerId: NodeRid): Boolean {
        return stateOf(peerId).isMaybeLegacy()
    }

    fun isConfirmedModern(peerId: NodeRid): Boolean {
        return stateOf(peerId).isConfirmedModern()
    }

    fun confirmModern(peerId: NodeRid) {
        stateOf(peerId).confirmedModern()
    }

    /**
     * Might blacklist this peer depending on the number of failures.
     *
     * @param peerId is the peer that's behaving badly
     * @param desc is the text we will log, surrounding the circumstances.
     *             (This could be caused by a bug, if so it has to be traced)
     */
    fun maybeBlacklist(peerId: NodeRid, desc: String) {
        stateOf(peerId).blacklist(desc, System.currentTimeMillis())
    }

    /**
     * Adds the peer if it doesn't exist. Do nothing if it exists.
     */
    fun addPeer(peerId: NodeRid) {
        stateOf(peerId)
    }

    fun isBlacklisted(xPeerId: NodeRid): Boolean {
        return stateOf(xPeerId).updateAndCheckBlacklisted()
    }

    fun getSyncableAndConnected(height: Long): Set<NodeRid> {
        return statuses.filterValues { it.isSyncable(height) && it.isConnected(System.currentTimeMillis()) }.map { it.key }.toSet()
    }

    fun markConnected(peerIds: Set<NodeRid>) {
        peerIds.forEach { stateOf(it).connected() }
    }

    fun markDisconnected(peerIds: Set<NodeRid>) {
        val now = System.currentTimeMillis()
        peerIds.forEach { stateOf(it).disconnected(now) }
    }

    fun clear() {
        if (logger.isDebugEnabled) {
            logger.debug("clearing all fast sync peer statuses")
        }
        statuses.clear()
    }

    fun reviveAllBlacklisted() {
        statuses.values.filter { it.isBlacklisted() }.forEach { it.whitelist() }
    }
}
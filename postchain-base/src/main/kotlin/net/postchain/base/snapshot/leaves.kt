// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.snapshot

import net.postchain.core.BlockEContext

abstract class LeafStore {
    abstract fun writeEvent(blockEContext: BlockEContext, data: ByteArray)
    abstract fun writeState(blockEContext: BlockEContext, state_n: Long, data: ByteArray)

    // delete events at and below given height
    abstract fun pruneEvents(blockEContext: BlockEContext, height: Long)

    // delete all states such that state_n is between left and right and state_height <= height
    abstract fun pruneStates(blockEContext: BlockEContext, left: Long, right: Long, height: Long)
}
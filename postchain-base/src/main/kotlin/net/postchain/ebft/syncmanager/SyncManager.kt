// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.syncmanager

import net.postchain.ebft.heartbeat.HeartbeatListener

interface SyncManager : HeartbeatListener {
    fun update()
}


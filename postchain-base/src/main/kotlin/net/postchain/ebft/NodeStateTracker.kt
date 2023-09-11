// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft

class NodeStateTracker {
    @Volatile
    var nodeStatuses: Array<NodeStatus>? = null
    @Volatile
    var myStatus: NodeStatus? = null
    @Volatile
    var blockHeight: Long = -1
}

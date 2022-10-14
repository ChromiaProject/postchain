// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft

class NodeStateTracker {
    var nodeStatuses: Array<NodeStatus>? = null
    var myStatus: NodeStatus? = null
    var blockHeight: Long = -1
}

// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.util

import kotlin.random.Random

object NodesTestHelper {

    val random = Random.Default

    fun selectAnotherRandNode(nodeId: Int, nodesCount: Int): Int {
        val randNode = random.nextInt(nodesCount)
        // Cannot be connected to itself, so pic new value
        return if (randNode == nodeId) (randNode + 1) % nodesCount else randNode
    }

}
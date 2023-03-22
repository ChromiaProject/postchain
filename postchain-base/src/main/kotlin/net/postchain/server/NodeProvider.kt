package net.postchain.server

import net.postchain.PostchainNode

fun interface NodeProvider {
    fun get(): PostchainNode
}
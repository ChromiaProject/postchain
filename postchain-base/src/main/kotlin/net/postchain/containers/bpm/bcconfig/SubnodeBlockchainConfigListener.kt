package net.postchain.containers.bpm.bcconfig

interface SubnodeBlockchainConfigListener {
    fun commit(height: Long)
}

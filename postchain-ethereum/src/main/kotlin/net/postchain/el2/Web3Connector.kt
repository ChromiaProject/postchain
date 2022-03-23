package net.postchain.el2

import org.web3j.protocol.Web3j

data class Web3Connector(val web3j: Web3j, val contractAddress: String) {
    fun shutdown() {
        web3j.shutdown()
    }
}
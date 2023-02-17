package net.postchain.debug

import net.postchain.common.BlockchainRid

interface NodeDiagnosticContext : MutableMap<DiagnosticProperty, DiagnosticValue> {
    fun format(): String

    fun blockchainErrorQueue(blockchainRid: BlockchainRid): DiagnosticQueue<String>
    fun blockchainData(blockchainRid: BlockchainRid): DiagnosticData
    fun removeBlockchainData(blockchainRid: BlockchainRid?): DiagnosticData?
    fun clearBlockchainData()
}

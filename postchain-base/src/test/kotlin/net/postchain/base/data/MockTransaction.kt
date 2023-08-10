package net.postchain.base.data

import net.postchain.core.Transaction
import net.postchain.core.TxEContext

data class MockTransaction(val id: Byte) : Transaction {
    override fun checkCorrectness() { }

    override fun isSpecial(): Boolean = false

    override fun apply(ctx: TxEContext): Boolean = true

    override fun getRawData(): ByteArray = ByteArray(0)

    override fun getRID(): ByteArray = ByteArray(32) { _ -> id }

    override fun getHash(): ByteArray = getRID()
}

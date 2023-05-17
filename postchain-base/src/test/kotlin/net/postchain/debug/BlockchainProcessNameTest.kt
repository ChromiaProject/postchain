package net.postchain.debug

import net.postchain.common.BlockchainRid
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class BlockchainProcessNameTest {

    val pubKey = "1234"
    val bcRid = BlockchainRid.buildFromHex("0011223344556677001122334455667700112233445566770011223344556677")

    @Test
    fun happy() {
        val psName = BlockchainProcessName(pubKey, bcRid)
        val short = psName.toString()
        assertEquals("[1234/00:6677]", short)
    }
}
// Copyright (c) 2026 ChromaWay AB. See README for license information.

package net.postchain.ebft

import net.postchain.base.BasePeerCommConfiguration
import net.postchain.common.BlockchainRid
import net.postchain.core.BadMessageException
import net.postchain.core.NodeRid
import net.postchain.crypto.Secp256K1CryptoSystem
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock

class EbftIdentPacketFreshnessTest {

    private val cryptoSystem = Secp256K1CryptoSystem()
    private val senderKeys = cryptoSystem.generateKeyPair()
    private val receiverKeys = cryptoSystem.generateKeyPair()
    private val blockchainRid = BlockchainRid.buildRepeat(1)
    private val now = 1_800_000_000_000L

    private fun codec(keys: net.postchain.crypto.KeyPair, time: Long) = EbftPacketCodec(
            BasePeerCommConfiguration(mock(), cryptoSystem, keys.privKey.data, keys.pubKey.data),
            blockchainRid
    ) { time }

    private fun identPacketSentAt(time: Long): ByteArray =
            codec(senderKeys, time).makeIdentPacket(NodeRid(receiverKeys.pubKey.data))

    @Test
    fun `fresh identification packet is accepted`() {
        val info = codec(receiverKeys, now).parseIdentPacket(identPacketSentAt(now))
        assertArrayEquals(senderKeys.pubKey.data, info.nodeId.data)
    }

    @Test
    fun `identification packet within the allowed clock skew is accepted`() {
        val skew = EbftPacketCodec.IDENT_PACKET_MAX_CLOCK_SKEW_MS
        codec(receiverKeys, now).parseIdentPacket(identPacketSentAt(now - skew))
        codec(receiverKeys, now).parseIdentPacket(identPacketSentAt(now + skew))
    }

    @Test
    fun `replayed old identification packet is rejected`() {
        val captured = identPacketSentAt(now)
        val later = now + EbftPacketCodec.IDENT_PACKET_MAX_CLOCK_SKEW_MS + 1
        assertThrows<BadMessageException> { codec(receiverKeys, later).parseIdentPacket(captured) }
    }

    @Test
    fun `identification packet from the far future is rejected`() {
        val future = now + EbftPacketCodec.IDENT_PACKET_MAX_CLOCK_SKEW_MS + 1
        assertThrows<BadMessageException> { codec(receiverKeys, now).parseIdentPacket(identPacketSentAt(future)) }
    }
}

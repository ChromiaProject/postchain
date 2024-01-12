package net.postchain.ebft

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import net.postchain.common.wrap
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.MessageTopic
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class EbftPacketCacheTest {

    private val pubKey1 = "1".toByteArray().wrap()
    private val pubKey2 = "2".toByteArray().wrap()

    private val message1 = "11".toByteArray()
    private val message2 = "22".toByteArray()

    private val ebftMmessage1: EbftMessage = mock()

    private lateinit var sut: EbftPacketCache

    @BeforeEach
    fun beforeEach() {
        sut = EbftPacketCache()
    }

    @Test
    fun `empty cache should return null`() {
        assertThat(sut.get(pubKey1, message1, MessageTopic.STATUS)).isNull()
    }

    @Test
    fun `put should return message`() {
        assertThat(sut.put(pubKey1, message1, ebftMmessage1)).isEqualTo(ebftMmessage1)
    }

    @Test
    fun `get should return message`() {
        // setup
        sut.put(pubKey1, message1, ebftMmessage1)
        // execute & verify
        assertThat(sut.get(pubKey1, message1, MessageTopic.STATUS)).isEqualTo(ebftMmessage1)
    }

    @Test
    fun `get with different message should return null`() {
        // setup
        sut.put(pubKey1, message1, ebftMmessage1)
        // execute & verify
        assertThat(sut.get(pubKey1, message2, MessageTopic.STATUS)).isNull()
    }

    @Test
    fun `get with different pubKey should return null`() {
        // setup
        sut.put(pubKey1, message1, ebftMmessage1)
        // execute & verify
        assertThat(sut.get(pubKey2, message1, MessageTopic.STATUS)).isNull()
    }
}
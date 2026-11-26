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

    @Test
    fun `cache evicts least recently used entries beyond max entries`() {
        val cache = EbftPacketCache(maxEntries = 2, maxBytes = 1_000, ttlMillis = 60_000)
        cache.put(pubKey1, "1".toByteArray(), ebftMmessage1)
        cache.put(pubKey2, "2".toByteArray(), ebftMmessage1)
        cache.get(pubKey1, "1".toByteArray(), MessageTopic.STATUS)
        cache.put("3".toByteArray().wrap(), "3".toByteArray(), ebftMmessage1)

        assertThat(cache.get(pubKey1, "1".toByteArray(), MessageTopic.STATUS)).isEqualTo(ebftMmessage1)
        assertThat(cache.get(pubKey2, "2".toByteArray(), MessageTopic.STATUS)).isNull()
        assertThat(cache.get("3".toByteArray().wrap(), "3".toByteArray(), MessageTopic.STATUS)).isEqualTo(ebftMmessage1)
    }

    @Test
    fun `cache evicts entries beyond max bytes`() {
        val cache = EbftPacketCache(maxEntries = 10, maxBytes = 8, ttlMillis = 60_000)
        cache.put(pubKey1, ByteArray(4), ebftMmessage1)
        cache.put(pubKey2, ByteArray(4), ebftMmessage1)
        cache.put("3".toByteArray().wrap(), ByteArray(4), ebftMmessage1)

        assertThat(cache.get(pubKey1, ByteArray(4), MessageTopic.STATUS)).isNull()
        assertThat(cache.get(pubKey2, ByteArray(4), MessageTopic.STATUS)).isEqualTo(ebftMmessage1)
        assertThat(cache.get("3".toByteArray().wrap(), ByteArray(4), MessageTopic.STATUS)).isEqualTo(ebftMmessage1)
    }

    @Test
    fun `cache does not store messages larger than max bytes`() {
        val cache = EbftPacketCache(maxEntries = 10, maxBytes = 4, ttlMillis = 60_000)

        assertThat(cache.put(pubKey1, ByteArray(5), ebftMmessage1)).isEqualTo(ebftMmessage1)
        assertThat(cache.get(pubKey1, ByteArray(5), MessageTopic.STATUS)).isNull()
    }

    @Test
    fun `cache expires entries after ttl`() {
        var now = 1_000L
        val cache = EbftPacketCache(maxEntries = 10, maxBytes = 1_000, ttlMillis = 100, clock = { now })
        cache.put(pubKey1, message1, ebftMmessage1)
        now += 101

        assertThat(cache.get(pubKey1, message1, MessageTopic.STATUS)).isNull()
    }
}
package net.postchain.base

import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals

class PeerInfoTest {

    @Test
    fun createFromGtvArray() {
        val peerInfo = PeerInfo("localhost", 5555, byteArrayOf(1), Instant.ofEpochMilli(123))
        val gtv = gtv(gtv(peerInfo.host), gtv(peerInfo.port.toLong()), gtv(peerInfo.pubKey), gtv(peerInfo.lastUpdated!!.toEpochMilli()))
        val actual = PeerInfo.fromGtv(gtv)
        assertEquals(peerInfo, actual)
    }

    @Test
    fun createFromGtvArray_noLastUpdated() {
        val peerInfo = PeerInfo("localhost", 5555, byteArrayOf(1), null)
        val gtv = gtv(gtv(peerInfo.host), gtv(peerInfo.port.toLong()), gtv(peerInfo.pubKey))
        val actual = PeerInfo.fromGtv(gtv)
        assertEquals(peerInfo, actual)
    }

    @Test
    fun createFromBadGtvArray() {
        val gtv = gtv(gtv("localhost"), gtv(5555L))
        assertThrows<IllegalArgumentException> {
            PeerInfo.fromGtv(gtv)
        }
    }

    @Test
    fun createFromGtvDictionary() {
        val peerInfo = PeerInfo("localhost", 5555, byteArrayOf(1), Instant.ofEpochMilli(123))
        val gtv = gtv(
                "host" to gtv(peerInfo.host),
                "port" to gtv(peerInfo.port.toLong()),
                "pubkey" to gtv(peerInfo.pubKey),
                "last_updated" to gtv(peerInfo.lastUpdated!!.toEpochMilli()))
        val actual = PeerInfo.fromGtv(gtv)
        assertEquals(peerInfo, actual)
    }

    @Test
    fun createFromExtraGtvDictionary() {
        val peerInfo = PeerInfo("localhost", 5555, byteArrayOf(1), Instant.ofEpochMilli(123))
        val gtv = gtv(
                "host" to gtv(peerInfo.host),
                "port" to gtv(peerInfo.port.toLong()),
                "pubkey" to gtv(peerInfo.pubKey),
                "last_updated" to gtv(peerInfo.lastUpdated!!.toEpochMilli()),
                "extra_filed" to gtv("extra value")
        )
        val actual = PeerInfo.fromGtv(gtv)
        assertEquals(peerInfo, actual)
    }

    @Test
    fun createFromGtvDictionary_noLastUpdated() {
        val peerInfo = PeerInfo("localhost", 5555, byteArrayOf(1))
        val gtv = gtv(
                "host" to gtv(peerInfo.host),
                "port" to gtv(peerInfo.port.toLong()),
                "pubkey" to gtv(peerInfo.pubKey))
        val actual = PeerInfo.fromGtv(gtv)
        assertEquals(peerInfo, actual)
    }

    @Test
    fun createFromBadGtvDictionary() {
        val gtv = gtv(
                "host" to gtv("host"),
                "foobar" to gtv(5555L), // foobar
                "pubkey" to gtv(byteArrayOf(1)))
        assertThrows<IllegalArgumentException> {
            PeerInfo.fromGtv(gtv)
        }
    }

    @Test
    fun createBadGtv() {
        assertThrows<IllegalArgumentException> { PeerInfo.fromGtv(GtvNull) }
        assertThrows<IllegalArgumentException> { PeerInfo.fromGtv(gtv(1)) }
        assertThrows<IllegalArgumentException> { PeerInfo.fromGtv(gtv("2")) }
    }
}
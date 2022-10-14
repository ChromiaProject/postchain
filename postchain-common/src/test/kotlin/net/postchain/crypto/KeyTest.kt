package net.postchain.crypto

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeyTest {

    @Test
    fun equalsAndHashCode() {
        val key1 = PubKey(ByteArray(33).apply { fill(1) })
        val key2 = PubKey(ByteArray(33).apply { fill(1) })
        val key3 = PubKey(ByteArray(33).apply { fill(2) })

        assertTrue(key1.equals(key2))
        assertTrue(key1.hashCode() == key2.hashCode())
        assertFalse(key1.equals(key3))
        assertFalse(key1.hashCode() == key3.hashCode())
    }

    @Test
    fun keyPairEqualsAndHashCode() {
        val keyPair1 = KeyPair(PubKey(ByteArray(33).apply { fill(1) }), PrivKey(ByteArray(32).apply { fill(1) }))
        val keyPair2 = KeyPair(PubKey(ByteArray(33).apply { fill(1) }), PrivKey(ByteArray(32).apply { fill(1) }))
        val keyPair3 = KeyPair(PubKey(ByteArray(33).apply { fill(2) }), PrivKey(ByteArray(32).apply { fill(2) }))

        assertTrue(keyPair1.equals(keyPair2))
        assertTrue(keyPair1.hashCode() == keyPair2.hashCode())
        assertFalse(keyPair1.equals(keyPair3))
        assertFalse(keyPair1.hashCode() == keyPair3.hashCode())
    }

}

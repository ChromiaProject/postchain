package net.postchain.gtv.mapper

import assertk.assert
import assertk.assertions.isEqualTo
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Test

class ObjectToGtvDictionaryTest {

    @Test
    fun basicMap() {
        assert(GtvObjectMapper.toGtvDictionary(mapOf("foo" to 1L))).isEqualTo(gtv(mapOf("foo" to gtv(1))))
    }
}

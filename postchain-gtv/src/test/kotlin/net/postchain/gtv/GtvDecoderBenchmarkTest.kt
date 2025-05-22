package net.postchain.gtv

import assertk.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.measureTimedValue

class GtvDecoderBenchmarkTest {

    @Test
    fun benchmark() {
        val gtvData = javaClass.getResourceAsStream("/large.gtv")!!.readBytes()
        println("Binary GTV size: ${gtvData.size} bytes")
        val (result, duration) = measureTimedValue { GtvDecoder.decodeGtv(gtvData) }
        assertThat(result.type == GtvType.DICT)
        println("Time: ${duration.inWholeMilliseconds} ms")
    }
}

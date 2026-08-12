package net.postchain.gtv.json

import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvGenerator
import net.postchain.gtv.VECTORS
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import net.postchain.gtv.json.gson.GtvJson as GsonJson
import net.postchain.gtv.json.jackson.GtvJson as JacksonJson

/**
 * The Gson and Jackson codecs against each other, on generated input.
 *
 * [net.postchain.gtv.GtvJsonCodecContract] pins each codec to the cases someone thought of; this pins the ones
 * nobody did. GTV is consensus-critical and the choice of JSON library is meant to be invisible, so "these two
 * write the same bytes" has to hold for values and documents no fixed data set contains. Gson escaping U+2028
 * where Jackson leaves it alone is exactly the kind of difference that only surfaces this way.
 *
 * Where the two disagree, Gson is right by definition: its output is the JSON that GTV has always been written
 * in. Each codec is also read through all three input forms, since one that disagrees with itself about a
 * document cannot agree with anything else about it.
 */
class JsonCodecEquivalenceTest {

    private val cases = System.getProperty("gtv.json.cases")?.toIntOrNull() ?: 500
    private val seed = System.getProperty("gtv.json.seed")?.toLongOrNull() ?: 20260812L

    /** Every combination that can change a byte of output, rather than only the four named presets. */
    private val configurations: List<Pair<GtvJsonConfig, Boolean>> = buildList {
        for (supportBigInteger in listOf(true, false)) {
            for (bigIntegerAsString in listOf(true, false)) {
                for (htmlSafe in listOf(true, false)) {
                    for (pretty in listOf(false, true)) {
                        val config = GtvJsonConfig(
                            bigIntegerAsString = bigIntegerAsString,
                            supportBigInteger = supportBigInteger,
                            htmlSafe = htmlSafe,
                        )
                        add(config to pretty)
                    }
                }
            }
        }
    }

    @Test
    fun writeTheSameBytesForTheReferenceVectors() {
        for (vector in VECTORS) {
            val gtv = GtvDecoder.decodeGtv(vector.der.hexStringToByteArray())
            for ((config, pretty) in configurations) checkWriting(gtv, config, pretty, vector.name)
        }
    }

    @Test
    fun writeTheSameBytesForGeneratedValues() {
        val generator = GtvGenerator(Random(seed))
        repeat(cases) { i ->
            val gtv = generator.next()
            for ((config, pretty) in configurations) checkWriting(gtv, config, pretty, "case #$i (seed $seed)")
        }
    }

    @Test
    fun readWhatEitherOfThemWroteAsTheSameValue() {
        val generator = GtvGenerator(Random(seed + 1))
        repeat(cases) { i ->
            val gtv = generator.next()
            for ((config, pretty) in configurations) {
                val written = runCatching { GsonJson(config, pretty).encodeToString(gtv) }.getOrNull() ?: continue
                checkReading(written, "case #$i (seed ${seed + 1}) written with $config pretty=$pretty")
            }
        }
    }

    @Test
    fun agreeOnHandPickedLenientDocuments() {
        for (text in LenientJsonGenerator.HAND_PICKED) checkReading(text, "hand-picked")
    }

    @Test
    fun agreeOnGeneratedLenientDocuments() {
        val generator = LenientJsonGenerator(Random(seed + 2))
        repeat(cases * 2) { i -> checkReading(generator.document(), "case #$i (seed ${seed + 2})") }
    }

    @Test
    fun agreeOnDamagedLenientDocuments() {
        // Most of these are rejected by both, which is the point: they have to be rejected by both.
        val generator = LenientJsonGenerator(Random(seed + 3))
        repeat(cases * 2) { i ->
            checkReading(generator.damaged(generator.document()), "case #$i (seed ${seed + 3})")
        }
    }

    @Test
    fun agreeOnNumbersTooLongForGsonsBuffer() {
        // Gson scans a number inside a 1024-character buffer and gives up when the token fills it, at which
        // point the token comes back as a string instead. Nothing about that is intentional, and all three
        // lengths have to agree anyway.
        for (length in listOf(1023, 1024, 1025)) {
            checkReading("9".repeat(length), "bare number of $length digits")
            checkReading("[" + "9".repeat(length) + "]", "array holding a number of $length digits")
        }
    }

    /** The three write forms of both codecs have to produce one document. */
    private fun checkWriting(gtv: Gtv, config: GtvJsonConfig, pretty: Boolean, what: String) {
        val where = "$what, config $config, pretty=$pretty"
        val gson = GsonJson(config, pretty)
        val jackson = JacksonJson(config, pretty)

        val expected = outcome { gson.encodeToString(gtv) }
        val actual = outcome { jackson.encodeToString(gtv) }
        if (expected is Outcome.Rejected && actual is Outcome.Rejected) return
        if (expected !is Outcome.Ok || actual !is Outcome.Ok) {
            fail("one codec wrote and the other refused, $where: gson=$expected jackson=$actual")
        }

        val text = expected.value
        assertEquals(text, actual.value, "encodeToString, $where")

        val bytes = text.encodeToByteArray()
        for ((name, codec) in listOf<Pair<String, GtvJsonCodec>>("gson" to gson, "jackson" to jackson)) {
            assertArrayEquals(bytes, codec.encodeToByteArray(gtv), "$name encodeToByteArray, $where")
            val stream = ByteArrayOutputStream()
            codec.encodeTo(gtv, stream)
            assertArrayEquals(bytes, stream.toByteArray(), "$name encodeTo, $where")
        }
    }

    /** Both codecs must reject [text], or both must read it as the same value, in both strictness modes. */
    private fun checkReading(text: String, what: String) {
        for (gsonCompatible in listOf(true, false)) {
            val config = GtvJsonConfig(supportBigInteger = true, gsonCompatible = gsonCompatible)
            val where = "$what, gsonCompatible=$gsonCompatible, input <<$text>>"
            val expected = read(GsonJson(config), text, "gson, $where")
            val actual = read(JacksonJson(config), text, "jackson, $where")
            agree(expected, actual, where)
        }
    }

    /** Reads through all three input forms, which must agree with each other before agreeing with anything. */
    private fun read(codec: GtvJsonCodec, text: String, what: String): Outcome<String> {
        val bytes = text.encodeToByteArray()
        val fromString = outcome { codec.decodeFromString(text).toString() }
        agree(fromString, outcome { codec.decodeFromByteArray(bytes).toString() }, "bytes vs string, $what")
        agree(
            fromString,
            outcome { codec.decodeFrom(ByteArrayInputStream(bytes)).toString() },
            "stream vs string, $what",
        )
        return fromString
    }

    /**
     * Rejection is compared as rejection and nothing more: a codec may turn the same input down in whatever
     * way it likes, and even one library's own input forms do not always fail identically.
     */
    private fun agree(expected: Outcome<String>, actual: Outcome<String>, what: String) {
        when {
            expected is Outcome.Rejected && actual is Outcome.Rejected -> Unit
            expected is Outcome.Ok && actual is Outcome.Ok -> assertEquals(expected.value, actual.value, what)
            else -> fail("$what: one read a value and the other refused: $expected vs $actual")
        }
    }
}

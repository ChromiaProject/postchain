package net.postchain.gtv.json.gson

import com.google.gson.Gson
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvGenerator
import net.postchain.gtv.GtvNull
import net.postchain.gtv.VECTORS
import net.postchain.gtv.json.GtvJsonConfig
import net.postchain.gtv.makeLenientGtvGson
import net.postchain.gtv.makeLenientGtvGsonBuilder
import net.postchain.gtv.makeStrictGtvGson
import net.postchain.gtv.make_gtv_gson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import kotlin.random.Random

/**
 * [GtvJson] against the `Gson` instances it is meant to replace.
 *
 * The codec builds its own `JsonElement` tree rather than going through [net.postchain.gtv.GtvAdapter], so the
 * two are separate implementations of one mapping and can drift. Everything written through the old API is on
 * the wire and in databases already, so they have to keep agreeing byte for byte.
 */
class GsonCodecMatchesLegacyBindingsTest {

    private val cases = System.getProperty("gtv.json.cases")?.toIntOrNull() ?: 500
    private val seed = System.getProperty("gtv.json.seed")?.toLongOrNull() ?: 20260812L

    private val legacy = listOf(
        Legacy("make_gtv_gson", GtvJson(GtvJsonConfig.Default)) { make_gtv_gson().toJson(it, Gtv::class.java) },
        Legacy("makeStrictGtvGson", GtvJson(GtvJsonConfig.Strict)) { makeStrictGtvGson().toJson(it, Gtv::class.java) },
        Legacy("makeLenientGtvGson", GtvJson(GtvJsonConfig.Lenient)) { makeLenientGtvGson().toJson(it, Gtv::class.java) },
        Legacy("lenientPretty", GtvJson(GtvJsonConfig.Lenient, prettyPrint = true)) {
            makeLenientGtvGsonBuilder().setPrettyPrinting().create().toJson(it, Gtv::class.java)
        },
    )

    @Test
    fun writesWhatTheGsonFactoriesWriteForTheReferenceVectors() {
        for (vector in VECTORS) {
            val gtv = GtvDecoder.decodeGtv(vector.der.hexStringToByteArray())
            checkAll(gtv, vector.name)
        }
    }

    @Test
    fun writesWhatTheGsonFactoriesWriteForGeneratedValues() {
        val random = Random(seed)
        val generator = GtvGenerator(random)
        repeat(cases) { i -> checkAll(generator.next(), "case #$i (seed $seed)") }
    }

    @Test
    fun readsWhatTheGsonFactoriesRead() {
        val generator = GtvGenerator(Random(seed + 1))
        val gson = makeLenientGtvGson()
        val codec = GtvJson(GtvJsonConfig.Lenient)
        repeat(cases) { i ->
            val written = codec.encodeToString(generator.next())
            val where = "case #$i (seed ${seed + 1}) reading <<$written>>"
            // A big_integer written as a bare number is out of range on the way back in, and both are
            // supposed to say so. What must not happen is one of them reading it as something.
            val expected = runCatching { readWithGson(gson, written) }
            val actual = runCatching { codec.decodeFromString(written).toString() }
            if (expected.isSuccess != actual.isSuccess) {
                fail(
                    "$where: gson=${expected.exceptionOrNull() ?: expected.getOrNull()}, " +
                        "codec=${actual.exceptionOrNull() ?: actual.getOrNull()}"
                )
            }
            if (expected.isSuccess) assertEquals(expected.getOrThrow(), actual.getOrThrow(), where)
        }
    }

    /**
     * Gson hands back Java null for a document that is just `null`, because a registered `JsonDeserializer`
     * is bypassed for JSON null and never gets to say [GtvNull]. Nested nulls do reach the adapter, so this
     * is a wart of the old entry point rather than of the mapping, and the codec is right to answer [GtvNull].
     */
    private fun readWithGson(gson: Gson, text: String): String =
        gson.fromJson(text, Gtv::class.java)?.toString() ?: GtvNull.toString()

    private fun checkAll(gtv: Gtv, what: String) {
        for (entry in legacy) {
            val expected = try {
                entry.write(gtv)
            } catch (e: RuntimeException) {
                // This factory refuses the value, so the codec configured to match it has to refuse it too,
                // though not necessarily in the same way. The other factories still get their turn.
                val written = runCatching { entry.codec.encodeToString(gtv) }.getOrNull()
                if (written != null) fail("${entry.name} refused $what but the codec wrote <<$written>>")
                continue
            }
            assertEquals(expected, entry.codec.encodeToString(gtv), "${entry.name} on $what")
        }
    }

    private class Legacy(val name: String, val codec: GtvJson, val write: (Gtv) -> String)
}

package net.postchain.gtv.json

import kotlin.random.Random

/**
 * Builds documents that `Gson.fromJson(String, Class)` accepts, most of which are not JSON.
 *
 * What Gson's lenient parser accepts is not written down anywhere and is not what any other JSON library calls
 * lenient, so hand-written expectations would be guesses, and a guess about an accepted format is how a format
 * quietly forks. Generated documents let the expectations be taken from Gson on the spot instead.
 *
 * Shipped in this module's test jar so every codec can be held to the same generated inputs.
 */
class LenientJsonGenerator(private val random: Random) {

    fun document(): String = gap() + value(0) + gap()

    /** Removes, inserts or replaces one character, which usually makes the document unreadable. */
    fun damaged(text: String): String {
        if (text.isEmpty()) return text
        val at = random.nextInt(text.length)
        return when (random.nextInt(3)) {
            0 -> text.removeRange(at, at + 1)
            1 -> text.substring(0, at) + DAMAGE.random(random) + text.substring(at)
            else -> text.substring(0, at) + DAMAGE.random(random) + text.substring(at + 1)
        }
    }

    private fun value(depth: Int): String = when (random.nextInt(if (depth >= 3) 6 else 8)) {
        0, 1 -> LITERALS.random(random)
        2, 3 -> quoted()
        4, 5 -> bare()
        6 -> array(depth + 1)
        else -> obj(depth + 1)
    }

    private fun array(depth: Int): String = (0 until random.nextInt(4)).joinToString(
        separator = separator(),
        prefix = "[" + gap(),
        postfix = gap() + "]",
    ) { value(depth) }

    private fun obj(depth: Int): String = (0 until random.nextInt(4)).joinToString(
        separator = separator(),
        prefix = "{" + gap(),
        postfix = gap() + "}",
    ) { name() + gap() + nameSeparator() + gap() + value(depth) }

    private fun name(): String = if (random.nextBoolean()) quoted() else bare()

    private fun bare(): String = BARE.random(random)

    private fun quoted(): String {
        val quote = if (random.nextBoolean()) "\'" else "\""
        return quote + QUOTED.random(random) + quote
    }

    private fun separator(): String = gap() + (if (random.nextBoolean()) "," else ";") + gap()

    private fun nameSeparator(): String = when (random.nextInt(4)) {
        0 -> "="
        1 -> "=>"
        else -> ":"
    }

    /** Whitespace, or a comment, or nothing: all three are invisible to Gson. */
    private fun gap(): String = when (random.nextInt(8)) {
        0 -> " "
        1 -> "\n"
        2 -> "\t"
        3 -> "/*c*/"
        4 -> "//c\n"
        5 -> "#c\n"
        else -> ""
    }

    companion object {
        /**
         * Documents worth trying on every run, whatever the seed. Most are borrowed from the places the
         * mapping is surprising: whole-valued decimals, bare tokens that almost parse as numbers, keywords
         * matched per character against either case, and repeated field names.
         */
        val HAND_PICKED: List<String> = listOf(
            // The number table: whole-valued decimals are integers, everything else about them is unchanged.
            "123", "123.0", "123.456", "123.0000000001", "9223372036854775807", "9223372036854775808",
            "-9223372036854775808", "-9223372036854775809", "1e3", "1E5", "-1E5", "-0", "0.0", "0.5",
            "01", "00", "0123", ".5", "1.", "1e", "1e+", "0x10", "-", "+1",
            "0e-9999", "0e-10000", "1e-10000", "0.0000000001e10",
            // Bare tokens, including the ones that almost parse as numbers.
            "12EF", "12E", "12eF", "bye", "NaN", "Infinity", "-Infinity", "truex", "nullish",
            // Keywords, matched per character against either case.
            "true", "false", "null", "True", "FALSE", "nUlL", "NULL", "TRUE",
            // Quoting.
            "\"hi\"", "'hi'", "''", "'it\\'s'", "\"a\\'b\"", "'\\u00E5'", "'a\\nb'", "'\\/'",
            "{'x':123}", "{y:'hi'}", "{y:bye}", "{'a':'b','a':'c'}", "{'a':[1,'two',{b:3}]}",
            // Separators, and the missing array element that reads as null.
            "[]", "{}", "{ }", "[1,]", "[,]", "[1,,2]", "[1;2]", "[a,b]", "[1,2]", "{a=1}", "{a=>1}",
            "{\"a\":1,}", "{a:1,b:2}",
            // Comments and the cross-site-scripting guard.
            "//c\nx", "#c\nx", "/*c*/x", "/*c*/ [1, /*x*/ 2]", ")]}'\nx", "/*c", "//c", "#c",
            // A comment ahead of the value is fine; behind it Gson has already gone strict again.
            "'a'#c\n", "1//c\n", "1/*c*/", "1 ", "1\n",
            // Rejected by Gson too.
            "", " ", "1 2", "[1 2]", "{\"a\":}", "{a b:1}", "[", "{", "\"unterminated", "'unterminated",
            "[1,2", "{'a':1", "\"\\q\"", "\"\\u00g5\"", "\"\\u00\"", "]", "}", ":", ",",
            // A repeated field name discards the earlier value before anything asks it to be a GTV integer.
            "{'a':1.5,'a':1}", "{'a':99999999999999999999,'a':1}", "{'a':1,'a':1.5}", "[{a:1.5,a:1}]",
            // Nesting, where the leniencies have to compose.
            "{a:[1;2],b:{c=>'d'},e:[,],f:12EF}",
        )

        private val DAMAGE =
            listOf('{', '}', '[', ']', ',', ';', ':', '=', '\'', '"', '\\', '/', '#', '.', 'e', '0', ' ')

        private val LITERALS = listOf(
            "0", "1", "-1", "123.0", "123.456", "1e3", "1E5", "-0", "01", "9223372036854775808",
            "true", "false", "null", "True", "NULL", "nUlL",
        )
        private val BARE = listOf("a", "bye", "12EF", "12E", "12eF", "NaN", "0x10", "key", "truex", "-", "+1", ".5")
        private val QUOTED =
            listOf("", "a", "hi there", "a\\'b", "a\\\"b", "\\u00e5", "a\\nb", "x:y", "<&>", "\\u2028")
    }
}

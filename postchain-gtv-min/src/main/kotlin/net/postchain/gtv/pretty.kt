package net.postchain.gtv

public const val INDENT: Int = 2

/** Multi-line rendering of the same textual notation [Gtv.toString] produces. */
public fun Gtv.pretty(nesting: Int = 0): String = when (this) {
    is GtvArray -> prettyArray(this, nesting)
    is GtvDictionary -> prettyDict(this, nesting)
    else -> toString()
}

private fun prettyArray(gtv: GtvArray, nesting: Int) =
    if (gtv.array.isEmpty()) {
        indent(nesting) + "[]"
    } else {
        gtv.array.joinToString(separator = ",\n", prefix = "[\n", postfix = "\n" + indent(nesting) + "]") {
            indent(nesting + 1) + it.pretty(nesting + 1)
        }
    }

private fun prettyDict(gtv: GtvDictionary, nesting: Int) =
    if (gtv.dict.isEmpty()) {
        indent(nesting) + "[:]"
    } else {
        gtv.dict.asIterable().joinToString(separator = ",\n", prefix = "[\n", postfix = "\n" + indent(nesting) + "]") {
            indent(nesting + 1) + "\"${escapeGtv(it.key)}\": ${it.value.pretty(nesting + 1)}"
        }
    }

private fun indent(nesting: Int) = " ".repeat(nesting * INDENT)

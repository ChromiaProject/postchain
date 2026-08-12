package net.postchain.gtv

import net.postchain.gtv.internal.ReverseBuffer
import net.postchain.gtv.internal.Tags
import net.postchain.gtv.internal.longToDerContent

/**
 * Responsible for turning GTV objects into binary data.
 */
public object GtvEncoder {
    public fun encodeGtv(v: Gtv): ByteArray {
        val buffer = ReverseBuffer(estimateSize(v))
        writeGtv(buffer, v)
        return buffer.toByteArray()
    }

    private fun writeGtv(out: ReverseBuffer, v: Gtv) {
        when (v) {
            is GtvNull -> {
                out.writeBytes(byteArrayOf(Tags.NULL, 0))
                finish(out, Tags.CHOICE_NULL, 2)
            }

            is GtvByteArray -> writeWrapped(out, Tags.CHOICE_BYTEARRAY, Tags.OCTET_STRING, v.bytearray)

            is GtvString -> writeWrapped(out, Tags.CHOICE_STRING, Tags.UTF8_STRING, v.string.encodeToByteArray())

            is GtvInteger -> writeWrapped(out, Tags.CHOICE_INTEGER, Tags.INTEGER, longToDerContent(v.integer))

            is GtvBigInteger -> writeWrapped(out, Tags.CHOICE_BIGINTEGER, Tags.INTEGER, v.integer.toByteArray())

            is GtvArray -> {
                val before = out.size
                for (i in v.array.indices.reversed()) writeGtv(out, v.array[i])
                finish(out, Tags.SEQUENCE, out.size - before)
                finish(out, Tags.CHOICE_ARRAY, out.size - before)
            }

            is GtvDictionary -> {
                val before = out.size
                val entries = v.dict.entries.toList()

                for (i in entries.indices.reversed()) {
                    val entry = entries[i]
                    val pairStart = out.size
                    writeGtv(out, entry.value)
                    writeWrapped(out, Tags.UTF8_STRING, entry.key.encodeToByteArray())
                    finish(out, Tags.SEQUENCE, out.size - pairStart)
                }

                finish(out, Tags.SEQUENCE, out.size - before)
                finish(out, Tags.CHOICE_DICT, out.size - before)
            }

            else -> throw GtvException("Cannot encode ${v::class.simpleName} (type ${v.type}) as GTV binary")
        }
    }

    /** Writes `tag length content`. */
    private fun writeWrapped(out: ReverseBuffer, tag: Byte, content: ByteArray) {
        out.writeBytes(content)
        finish(out, tag, content.size)
    }

    /** Writes `outerTag { innerTag length content }`, i.e. an explicitly tagged CHOICE alternative. */
    private fun writeWrapped(out: ReverseBuffer, outerTag: Byte, innerTag: Byte, content: ByteArray) {
        out.writeBytes(content)
        finish(out, innerTag, content.size)
        finish(out, outerTag, content.size + lengthFieldSize(content.size) + 1)
    }

    private fun finish(out: ReverseBuffer, tag: Byte, contentLength: Int) {
        out.writeLength(contentLength)
        out.writeByte(tag)
    }

    private fun lengthFieldSize(length: Int): Int = when {
        length < 0x80 -> 1
        length < 0x100 -> 2
        length < 0x10000 -> 3
        length < 0x1000000 -> 4
        else -> 5
    }

    /** Rough upper bound, only used to pick an initial buffer size. */
    private fun estimateSize(v: Gtv): Int = when (v) {
        is GtvNull -> 8
        is GtvByteArray -> v.bytearray.size + 12
        is GtvString -> v.string.length * 3 + 12
        is GtvInteger -> 16
        is GtvBigInteger -> 32
        is GtvArray -> 16 + v.array.sumOf { estimateSize(it) }
        is GtvDictionary -> 16 + v.dict.entries.sumOf { it.key.length * 3 + 12 + estimateSize(it.value) }
        else -> 64
    }
}

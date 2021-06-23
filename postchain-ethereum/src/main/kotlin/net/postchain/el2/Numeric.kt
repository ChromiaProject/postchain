package net.postchain.utils

class Numeric {

    companion object {
        private const val HEX_PREFIX = "0x"
        private val HEX_CHAR_MAP = "0123456789abcdef".toCharArray()

        private fun containsHexPrefix(input: String): Boolean {
            return (!Strings.isEmpty(input)
                    && input.length > 1 && input[0] == '0' && input[1] == 'x')
        }

        fun cleanHexPrefix(input: String): String {
            return if (containsHexPrefix(input)) {
                input.substring(2)
            } else {
                input
            }
        }

        fun prependHexPrefix(input: String): String {
            return if (!containsHexPrefix(input)) {
                HEX_PREFIX + input
            } else {
                input
            }
        }

        fun toHexString(input: ByteArray, offset: Int, length: Int, withPrefix: Boolean): String {
            val output = String(toHexCharArray(input, offset, length)!!)
            return if (withPrefix) StringBuilder(HEX_PREFIX).append(output).toString() else output
        }

        private fun toHexCharArray(input: ByteArray, offset: Int, length: Int): CharArray? {
            val output = CharArray(length shl 1)
            var i = offset
            var j = 0
            while (i < length) {
                val v: Int = input[i].toInt() and 0xFF
                output[j++] = HEX_CHAR_MAP[v ushr 4]
                output[j] = HEX_CHAR_MAP[v and 0x0F]
                i++
                j++
            }
            return output
        }

        fun toHexString(input: ByteArray): String {
            return toHexString(input, 0, input.size, true)
        }
    }


}
package net.postchain.gtv.parse

import net.postchain.common.hexStringToByteArray
import java.math.BigInteger

/**
 * The token types live inside the companion object, as they always have, so that
 * `Token.Companion.LBracket` and friends keep resolving. That also means [Token.Companion.String] shadows
 * `kotlin.String` in here, hence the qualified return types.
 */
public sealed interface Token {
    public companion object {
        public data object LBracket : Token {
            override fun toString(): kotlin.String = "["
        }

        public data object RBracket : Token {
            override fun toString(): kotlin.String = "]"
        }

        public data object Comma : Token {
            override fun toString(): kotlin.String = ","
        }

        public data object Colon : Token {
            override fun toString(): kotlin.String = ":"
        }

        public data object Null : Token {
            override fun toString(): kotlin.String = "null"
        }

        public data object True : Token {
            override fun toString(): kotlin.String = "true"
        }

        public data object False : Token {
            override fun toString(): kotlin.String = "false"
        }

        public data class Integer(val v: Long) : Token

        public data class BigInteger(val v: java.math.BigInteger) : Token

        public data class ByteArray(val v: kotlin.ByteArray) : Token {
            override fun equals(other: Any?): Boolean =
                this === other || (other is ByteArray && v.contentEquals(other.v))

            override fun hashCode(): Int = v.contentHashCode()
        }

        public data class String(val v: kotlin.String) : Token
    }
}

public fun lexer(input: String): Sequence<Token> = sequence {
    var index = 0

    fun peek(): Char? = if (index < input.length) input[index] else null

    fun consume(): Char {
        val c = peek()
        index++
        return requireNotNull(c) { "Unexpected end of input" }
    }

    fun consume(length: Int): String = buildString {
        repeat(length) { append(consume()) }
    }

    fun expect(char: Char) {
        if (consume() != char) throw IllegalArgumentException("Unexpected input: ${input.substring(index)}")
    }

    fun expectOneOf(vararg chars: Char): Char {
        val actual = consume()
        if (!chars.contains(actual)) throw IllegalArgumentException("Unexpected input: ${input.substring(index)}")
        return actual
    }

    while (index < input.length) {
        val c = consume()
        when {
            c == '[' -> yield(Token.Companion.LBracket)

            c == ']' -> yield(Token.Companion.RBracket)

            c == ',' -> yield(Token.Companion.Comma)

            c == ':' -> yield(Token.Companion.Colon)

            c == 'n' -> {
                expect('u'); expect('l'); expect('l')
                yield(Token.Companion.Null)
            }

            c == 't' -> {
                expect('r'); expect('u'); expect('e')
                yield(Token.Companion.True)
            }

            c == 'f' -> {
                expect('a'); expect('l'); expect('s'); expect('e')
                yield(Token.Companion.False)
            }

            c.isDigit() || c == '-' -> {
                val digits = buildString {
                    append(c)
                    while (peek()?.isDigit() == true) append(consume())
                }
                if (peek() == 'L') {
                    consume()
                    yield(Token.Companion.BigInteger(BigInteger(digits)))
                } else {
                    yield(Token.Companion.Integer(digits.toLong()))
                }
            }

            c == 'x' -> {
                val quote = expectOneOf('"', '\'')
                val hex = buildString {
                    while (true) {
                        val c2 = consume()
                        if (c2 == quote) break else append(c2)
                    }
                }
                yield(Token.Companion.ByteArray(hex.hexStringToByteArray()))
            }

            c == '"' || c == '\'' -> {
                val text = buildString {
                    while (true) {
                        val c2 = consume()
                        if (c2 == c) break
                        if (c2 == '\\') {
                            when (consume()) {
                                '\'' -> append('\'')
                                '"' -> append('"')
                                '\\' -> append('\\')
                                'b' -> append('\b')
                                'n' -> append('\n')
                                't' -> append('\t')
                                'r' -> append('\r')
                                // Bug-compatible with the lexer this replaced. Its `sb.append(Integer.parseInt(consume(4)), 16)`
                                // looks like a misplaced paren for a radix, but it resolves to Kotlin's
                                // `StringBuilder.append(vararg Any?)`, so it appends the decimal digits of the
                                // parsed number and then a literal "16": "\u0041" reads as `4116`, not `A`.
                                // Non-decimal digits throw. Verified against postchain-gtv 3.49.18.
                                'u' -> {
                                    append(consume(4).toInt())
                                    append(16)
                                }
                            }
                        } else {
                            append(c2)
                        }
                    }
                }
                yield(Token.Companion.String(text))
            }

            c.isWhitespace() -> {} // ignore whitespace

            else -> throw IllegalArgumentException("Unrecognized token: ${input.substring(index)}")
        }
    }
}

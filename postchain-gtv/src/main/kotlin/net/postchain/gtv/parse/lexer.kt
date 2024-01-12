package net.postchain.gtv.parse

import net.postchain.common.hexStringToByteArray
import java.lang.Long.parseLong
import java.math.BigInteger

sealed interface Token {
    companion object {
        object LBracket : Token {
            override fun toString() = "["
        }

        object RBracket : Token {
            override fun toString() = "]"
        }

        object Comma : Token {
            override fun toString() = ","
        }

        object Colon : Token {
            override fun toString() = ":"
        }

        object Null : Token {
            override fun toString() = "null"
        }

        data class Integer(val v: Long) : Token
        data class BigInteger(val v: java.math.BigInteger) : Token
        data class ByteArray(val v: kotlin.ByteArray) : Token
        data class String(val v: kotlin.String) : Token
    }
}

fun lexer(input: String): Sequence<Token> = sequence {
    var index = 0

    fun peek(): Char? = if (index < input.length)
        input[index]
    else
        null

    fun consume(): Char {
        val c = peek()
        index++
        return c ?: throw IllegalArgumentException("Unexpected end of input")
    }

    fun consume(length: Int): String {
        val sb = StringBuilder()
        for (i in 0 until length) {
            sb.append(consume())
        }
        return sb.toString()
    }

    fun expect(char: Char) {
        if (consume() != char) throw IllegalArgumentException("Unexpected input: ${input.substring(index, input.length)}")
    }

    while (index < input.length) {
        val c = consume()
        when {
            c == '[' -> yield(Token.Companion.LBracket)

            c == ']' -> yield(Token.Companion.RBracket)

            c == ',' -> yield(Token.Companion.Comma)

            c == ':' -> yield(Token.Companion.Colon)

            c == 'n' -> {
                expect('u')
                expect('l')
                expect('l')
                yield(Token.Companion.Null)
            }

            c.isDigit() || c == '-' -> {
                val sb = StringBuilder()
                sb.append(c)
                while (true) {
                    if (peek()?.isDigit() == true) {
                        sb.append(consume())
                    } else {
                        break
                    }
                }
                if (peek() == 'L') {
                    consume()
                    yield(Token.Companion.BigInteger(BigInteger(sb.toString())))
                } else {
                    yield(Token.Companion.Integer(parseLong(sb.toString())))
                }
            }

            c == 'x' -> {
                expect('"')
                val sb = StringBuilder()
                while (true) {
                    val c2 = consume()
                    if (c2 == '"') {
                        break
                    } else {
                        sb.append(c2)
                    }
                }
                yield(Token.Companion.ByteArray(sb.toString().hexStringToByteArray()))
            }

            c == '"' -> {
                val sb = StringBuilder()
                while (true) {
                    when (val c2 = consume()) {
                        '"' -> {
                            break
                        }

                        '\\' -> {
                            when (consume()) {
                                '\'' -> sb.append('\'')
                                '\"' -> sb.append('\"')
                                '\\' -> sb.append('\\')
                                'b' -> sb.append('\b')
                                'n' -> sb.append('\n')
                                't' -> sb.append('\t')
                                'r' -> sb.append('\r')
                                'u' -> sb.append(Integer.parseInt(consume(4)), 16)
                            }
                        }

                        else -> sb.append(c2)
                    }
                }
                yield(Token.Companion.String(sb.toString()))
            }

            c.isWhitespace() -> {} // ignore whitespace

            else -> throw IllegalArgumentException("Unrecognized token: ${input.substring(index, input.length)}")
        }
    }
}

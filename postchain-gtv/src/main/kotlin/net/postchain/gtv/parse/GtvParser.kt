package net.postchain.gtv.parse

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvBigInteger
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvCollection
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvNull
import net.postchain.gtv.GtvString

class GtvParser private constructor(str: String) {
    private val tokens = lexer(str).toList()
    private var index = 0

    companion object {
        fun parse(str: String): Gtv {
            val parser = GtvParser(str)
            val value = parser.value() ?: throw IllegalArgumentException("Unexpected token: ${parser.consume()}")
            if (parser.peek() != null) throw IllegalArgumentException("Unexpected token at end: ${parser.consume()}")
            return value
        }
    }

    fun peek(n: Int = 0): Token? = if (index + n < tokens.size)
        tokens[index + n]
    else
        null

    fun consume(): Token {
        val t = peek()
        index++
        return t ?: throw IllegalArgumentException("Unexpected end of input")
    }

    fun expect(token: Token) {
        val t = consume()
        if (t != token) throw IllegalArgumentException("Unexpected token: $t}")
    }

    fun value(): Gtv? = primitive() ?: arrayOrDict()

    fun primitive(): Gtv? = when (val token = peek()) {
        is Token.Companion.Null -> {
            consume(); GtvNull
        }

        is Token.Companion.Integer -> {
            consume(); GtvInteger(token.v)
        }

        is Token.Companion.BigInteger -> {
            consume(); GtvBigInteger(token.v)
        }

        is Token.Companion.ByteArray -> {
            consume(); GtvByteArray(token.v)
        }

        is Token.Companion.String -> {
            consume(); GtvString(token.v)
        }

        else -> null
    }

    private fun arrayOrDict(): GtvCollection? = if (peek() == Token.Companion.LBracket) {
        when (peek(1)) {
            Token.Companion.Colon -> {
                consume()
                consume()
                expect(Token.Companion.RBracket)
                GtvDictionary.build(mapOf())
            }

            is Token.Companion.String -> {
                if (peek(2) == Token.Companion.Colon) {
                    dict()
                } else {
                    array()
                }
            }

            else -> array()
        }
    } else {
        null
    }

    fun array(): GtvArray {
        expect(Token.Companion.LBracket)
        val list = buildList {
            while (true) {
                if (peek() == Token.Companion.RBracket) break
                add(value() ?: throw IllegalArgumentException("Unexpected token: ${peek()}}"))
                if (peek() == Token.Companion.Comma) consume()
            }
        }
        expect(Token.Companion.RBracket)
        return GtvArray(list.toTypedArray())
    }

    fun dict(): GtvDictionary {
        expect(Token.Companion.LBracket)
        val map = buildMap {
            while (true) {
                if (peek() == Token.Companion.RBracket) break
                val (key, value) = keyAndValue()
                put(key, value)
                if (peek() == Token.Companion.Comma) consume()
            }
        }
        expect(Token.Companion.RBracket)
        return GtvDictionary.build(map)
    }

    private fun keyAndValue(): Pair<String, Gtv> {
        val key = consume()
        if (key !is Token.Companion.String)
            throw IllegalArgumentException("Unexpected token: $key}")
        expect(Token.Companion.Colon)
        val value = value() ?: throw IllegalArgumentException("Unexpected token: ${peek()}}")
        return key.v to value
    }
}

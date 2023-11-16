package net.postchain.gtv.parse

import com.github.h0tk3y.betterParse.combinators.map
import com.github.h0tk3y.betterParse.combinators.or
import com.github.h0tk3y.betterParse.combinators.separated
import com.github.h0tk3y.betterParse.combinators.times
import com.github.h0tk3y.betterParse.combinators.unaryMinus
import com.github.h0tk3y.betterParse.combinators.use
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken
import com.github.h0tk3y.betterParse.parser.ParseException
import com.github.h0tk3y.betterParse.parser.Parser
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvBigInteger
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvNull
import net.postchain.gtv.GtvString
import java.math.BigInteger

object GtvParser {

    private val gtvGrammar = object : Grammar<Gtv>() {
        val `null` by literalToken("null")
        val byteArray by regexToken("""x"[0-9A-Fa-f]*"""")
        val bigInteger by regexToken("""[0-9]+L""")
        val integer by regexToken("""[0-9]+""")
        val string by regexToken(""""[^"]*"""") // TODO escapes
        val whiteSpace by regexToken("\\s+", ignore = true)
        val id by regexToken("""\w+""")
        val lBracket by literalToken("[")
        val rBracket by literalToken("]")
        val lBrace by literalToken("{")
        val rBrace by literalToken("}")
        val comma by regexToken(",")
        val equals by literalToken("=")
        val colon by literalToken(":")
        val dquote by literalToken("\"")

        val primitive by (`null` use { GtvNull }) or
                (byteArray use { GtvByteArray(text.substring(2, text.length - 1).hexStringToByteArray()) }) or
                (bigInteger use { GtvBigInteger(BigInteger(text.dropLast(1))) }) or
                (integer use { GtvInteger(text.toLong()) }) or
                (string use { GtvString(text.substring(1, text.length - 1)) })

        val array: Parser<GtvArray> by ((-lBracket * separated(parser(this::gtv), comma, acceptZero = true) * -rBracket).use {
            GtvArray(terms.toTypedArray())
        })

        val dictPair1: Parser<Pair<String, Gtv>> by (id * -equals * parser(this::gtv)).map { (left, right) -> left.text to right }

        val dictionary1: Parser<GtvDictionary> by ((-lBrace * separated(parser(this::dictPair1), comma, acceptZero = true) * -rBrace).use {
            GtvDictionary.build(terms.toMap())
        })

        val dictPair2: Parser<Pair<String, Gtv>> by (-dquote * id * -dquote * -colon * parser(this::gtv)).map { (left, right) -> left.text to right }

        val dictionary2: Parser<GtvDictionary> by ((-lBracket * separated(parser(this::dictPair2), comma, acceptZero = true) * -rBracket).use {
            GtvDictionary.build(terms.toMap())
        })

        val gtv by primitive or array or dictionary1 or dictionary2

        override val rootParser by gtv
    }

    fun parse(str: String) = try {
        gtvGrammar.parseToEnd(str)
    } catch (e: ParseException) {
        throw IllegalArgumentException(e.message, e)
    }
}

// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import net.postchain.common.exception.UserMistake
import java.math.BigInteger

/**
 * Just a base class for all GTVs.
 */
abstract class AbstractGtv : Gtv {

    override operator fun get(index: Int): Gtv {
        throw UserMistake(errorMessage("array"))
    }

    override operator fun get(key: String): Gtv? {
        throw UserMistake(errorMessage("dict"))
    }

    override fun asString(): String {
        throw UserMistake(errorMessage("string"))
    }

    override fun asArray(): Array<out Gtv> {
        throw UserMistake(errorMessage("array"))
    }

    override fun isNull(): Boolean {
        return false
    }

    override fun asDict(): Map<String, Gtv> {
        throw UserMistake(errorMessage("dict"))
    }

    override fun asInteger(): Long {
        throw UserMistake(errorMessage("integer"))
    }

    override fun asBigInteger(): BigInteger {
        throw UserMistake(errorMessage("big integer"))
    }

    override fun asBoolean(): Boolean {
        throw UserMistake(errorMessage("boolean"))
    }

    override fun asByteArray(convert: Boolean): ByteArray {
        throw UserMistake(errorMessage("byte array"))
    }

    override fun nrOfBytes(): Int {
        throw UserMistake("Implementation expected")
    }

    private fun errorMessage(expectedType: String) = "Type error: $expectedType expected, found $type with value ${toString()}"
}

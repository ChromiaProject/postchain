// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import net.postchain.common.exception.UserMistake
import java.math.BigInteger

/**
 * Just a base class for all GTVs.
 */
abstract class AbstractGtv : Gtv {

    override operator fun get(index: Int): Gtv {
        throw UserMistake("Type error: array expected")
    }

    override operator fun get(key: String): Gtv? {
        throw UserMistake("Type error: dict expected")
    }

    override fun asString(): String {
        throw UserMistake("Type error: string expected")
    }

    override fun asArray(): Array<out Gtv> {
        throw UserMistake("Type error: array expected")
    }

    override fun isNull(): Boolean {
        return false
    }

    override fun asDict(): Map<String, Gtv> {
        throw UserMistake("Type error: dict expected")
    }

    override fun asInteger(): Long {
        throw UserMistake("Type error: integer expected")
    }

    override fun asBigInteger(): BigInteger {
        throw UserMistake("Type error: big integer expected")
    }

    override fun asBoolean(): Boolean {
        throw UserMistake("Type error: boolean expected")
    }

    override fun asByteArray(convert: Boolean): ByteArray {
        throw UserMistake("Type error: byte array expected")
    }

    override fun nrOfBytes(): Int {
        throw UserMistake("Implementation expected")
    }
}

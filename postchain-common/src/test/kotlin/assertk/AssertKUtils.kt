// Copyright (c) 2020 ChromaWay AB. See README for license information.

package assertk

import assertk.assertions.support.expected
import assertk.assertions.support.show

/**
 * Asserts the ByteArray content is equal to the content of expected one.
 * @see [ByteArray.contentEquals] function
 */
fun Assert<ByteArray>.isContentEqualTo(expected: ByteArray) = given { actual -> 
    if (actual.contentEquals(expected)) return
    expected("ByteArray:${show(expected)} but was ByteArray:${show(actual)}")
}

/**
 * Asserts the Array content is equal to the content of expected one.
 * @see [Array.contentEquals] function
 */
fun <T> Assert<Array<out T>>.isContentEqualTo(expected: Array<out T>) = given { actual ->
    if (actual.contentEquals(expected)) return
    expected("array:${show(expected)} but was array:${show(actual)}")
}

// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.core

open class ProgrammerMistake(message: String, cause: Exception? = null) : RuntimeException(message, cause)

open class UserMistake(message: String, cause: Exception? = null) : RuntimeException(message, cause)

/**
 * Used when the format of some data is incorrect, see [BadDataType] for examples
 */
open class BadDataMistake(val type: BadDataType, message: String, cause: Exception? = null): RuntimeException(message, cause)


enum class BadDataType (val type: Int) {
    BAD_GTV(1), // Something wrong on GTV level, for example GtvDictionary is broken.
    BAD_GTX(2), // A TX is incorrectly represented (even though the GTV itself is correct)
    BAD_BLOCK(3), // The block's format is incorrect in some way (including header errors)
    BAD_CONFIGURATION(4), // The blockchain configuration's format is not allowed.
    MISSING_DEPENDENCY(5), // We don't have all dependencies required to process this block
    GIVEN_DEPENDENCY_HEIGHT_TOO_LOW(6), // The block we received has a dependency of a height that is lower than the last block
    OTHER(100) // Please don't use, consider adding a new type instead
}
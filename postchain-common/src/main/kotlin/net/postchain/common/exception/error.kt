package net.postchain.common.exception

import net.postchain.common.toHex

open class UserMistake(message: String, cause: Exception? = null) : RuntimeException(message, cause)

/**
 * When the TX doesn't pass the "checkCorrectness()" check.
 * Could be a common error, so don't log this in full.
 */
class TransactionIncorrect(txRid: ByteArray, message: String? = null, cause: Exception? = null) :
        UserMistake("Transaction ${txRid.toHex()} is not correct${if (message != null) ": $message" else ""}", cause)

/**
 * When the TX failed during runtime. This could be a common error, since we must assume people will
 * try to send many crazy things to the Dapp, so don't log this in full.
 */
class TransactionFailed(message: String, cause: Exception? = null) : UserMistake(message, cause)

class NotFound(message: String, cause: Exception? = null) : UserMistake(message, cause)

class AlreadyExists(message: String, cause: Exception? = null) : UserMistake(message, cause)

open class ProgrammerMistake(message: String, cause: Exception? = null) : RuntimeException(message, cause)

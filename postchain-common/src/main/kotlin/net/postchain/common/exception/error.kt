package net.postchain.common.exception

open class UserMistake(message: String, cause: Exception? = null) : RuntimeException(message, cause)

/**
 * When the TX doesn't even pass the "isCorrect()" check.
 * Could be a common error, so don't log this in full.
 */
class TransactionIncorrect(message: String, cause: Exception? = null) : UserMistake(message, cause)

/**
 * When the TX failed during runtime. This could be a common error, since we must assume people will
 * try to send many crazy things to the Dapp, so don't log this in full.
 */
class TransactionFailed(message: String, cause: Exception? = null) : UserMistake(message, cause)

open class ProgrammerMistake(message: String, cause: Exception? = null) : RuntimeException(message, cause)

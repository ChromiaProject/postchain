package net.postchain.common.exception

open class UserMistake(message: String, cause: Exception? = null) : RuntimeException(message, cause)

open class ProgrammerMistake(message: String, cause: Exception? = null) : RuntimeException(message, cause)

package net.postchain.server

import net.postchain.common.exception.UserMistake

class NotFound(message: String, cause: Exception? = null) : UserMistake(message, cause)

class AlreadyExists(message: String, cause: Exception? = null) : UserMistake(message, cause)

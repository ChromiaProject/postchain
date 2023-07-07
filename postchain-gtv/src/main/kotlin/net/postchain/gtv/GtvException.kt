package net.postchain.gtv

import net.postchain.common.exception.UserMistake
import java.io.IOException

class GtvException(message: String) : IOException(message)
class GtvTypeException(message: String, cause: Exception? = null) : UserMistake(message, cause)

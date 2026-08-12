package net.postchain.gtv

import net.postchain.common.exception.UserMistake
import java.io.IOException

public class GtvException(message: String) : IOException(message)
public class GtvTypeException(message: String, cause: Exception? = null) : UserMistake(message, cause)

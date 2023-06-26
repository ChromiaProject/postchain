// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.common.exception.ProgrammerMistake

open class PmEngineIsAlreadyClosed(message: String, cause: Exception? = null) : ProgrammerMistake(message, cause)

/**
 * Used when the format of some data is incorrect
 */
abstract class BadDataException(message: String, cause: Exception? = null) : RuntimeException(message, cause)
class BadBlockException(message: String, cause: Exception? = null) : BadDataException(message, cause)
class BadBlockRIDAtHeightException(message: String, cause: Exception? = null) : BadDataException(message, cause)
class BadConfigurationException(message: String, cause: Exception? = null) : BadDataException(message, cause)
class BadMessageException(message: String, cause: Exception? = null) : BadDataException(message, cause)
class ConfigurationMismatchException(message: String, cause: Exception? = null) : BadDataException(message, cause)
class FailedConfigurationMismatchException(message: String, cause: Exception? = null) : BadDataException(message, cause)
class MissingDependencyException(message: String, cause: Exception? = null) : BadDataException(message, cause)
class MissingPeerInfoException(message: String, cause: Exception? = null) : BadDataException(message, cause)
class PrevBlockMismatchException(message: String, cause: Exception? = null) : BadDataException(message, cause)
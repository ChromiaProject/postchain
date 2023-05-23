package net.postchain.server.cli

open class CliException(override val message: String, override val cause: Exception? = null) : RuntimeException(message, cause)
// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

sealed class CliResult(open val code: Int = -1)
sealed class CliError(open val message: String? = null) : CliResult() {


    companion object {
        open class CliException(override val message: String, override val cause: Exception? = null) : RuntimeException(message, cause)
    }
}

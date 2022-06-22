package net.postchain.cli.util

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.OptionDelegate

/**
 * This will format all command options.
 * Command arguments are not considered.
 */
fun CliktCommand.formatOptions(): String {
    return registeredOptions()
        .filterIsInstance(OptionDelegate::class.java)
        .filter { it.value != null }
        .map { option -> "${option.names.maxByOrNull { it.length }?.trimStart('-')}=${option.value}" }
        .sorted()
        .joinToString(separator = ", ")
}

fun CliktCommand.printCommandInfo() {
    println("$commandName will be executed with: ${formatOptions()}")
}

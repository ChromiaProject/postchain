package net.postchain.containers.bpm.command

interface CommandExecutor {
    fun runCommand(cmd: Array<String>): String?

    fun runCommandWithOutput(cmd: Array<String>): CommandResult
}

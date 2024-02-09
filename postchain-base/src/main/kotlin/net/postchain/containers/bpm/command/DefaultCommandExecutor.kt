package net.postchain.containers.bpm.command

import mu.KLogging
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object DefaultCommandExecutor : KLogging(), CommandExecutor {

    override fun runCommand(cmd: Array<String>): String? {
        logger.debug("Executing command: ${cmd.contentToString()}")
        return try {
            val process = Runtime.getRuntime().exec(cmd)
            process.waitFor(10, TimeUnit.SECONDS)
            return if (process.exitValue() != 0) {
                String(process.errorStream.readAllBytes())
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error("Unable to run command: ${cmd.contentToString()} Error: $e")
            e.toString()
        }
    }

    override fun runCommandWithOutput(cmd: Array<String>): CommandResult {
        logger.debug("Executing command: ${cmd.contentToString()}")
        return try {
            val process = Runtime.getRuntime().exec(cmd)
            process.waitFor(10, TimeUnit.SECONDS)
            val systemOut = BufferedReader(InputStreamReader(process.inputStream))
            val systemErr = BufferedReader(InputStreamReader(process.errorStream))
            CommandResult(process.exitValue(), systemOut.readLines(), systemErr.readLines())
        } catch (e: Exception) {
            logger.error("Unable to run command: ${cmd.contentToString()} Error: $e")
            CommandResult(-1, listOf(), listOf(e.toString()))
        }
    }
}

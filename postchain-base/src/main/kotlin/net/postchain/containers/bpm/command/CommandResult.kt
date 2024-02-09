package net.postchain.containers.bpm.command

data class CommandResult(val exitValue: Int, val systemOut: List<String>, val systemErr: List<String>)

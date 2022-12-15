package net.postchain.client.cli

import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file

fun ParameterHolder.configFileOption() = option("-c", "--config", help = "Configuration *.properties of node and blockchain")
        .file(mustExist = true)

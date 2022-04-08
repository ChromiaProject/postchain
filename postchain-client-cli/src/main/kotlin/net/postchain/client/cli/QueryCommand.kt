package net.postchain.client.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.transformAll
import net.postchain.gtv.Gtv

class QueryCommand : CliktCommand(name = "query", help = "Make a query towards a postchain node") {
    private val configFile by configFileOption()

    private val queryName by argument(help = "name of the query to make.")

    private val args by argument(help = "arguments to pass to the query. The dict is passed either as key-value pairs or as a singe dict element.")
            .multiple()
            .transformAll { createDict(it) }

    private fun createDict(args: List<String>): Gtv {
        if (args.size == 1 && args[0].startsWith("{") && args[0].endsWith("}")) return encodeArg(args[0])
        return encodeArg("{${args.joinToString(",")}}")
    }

    override fun run() {

    }
}
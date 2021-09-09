// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.cli

import com.beust.jcommander.Parameters

@Parameters(commandDescription = "Configures node")
class CommandConfigureNode : Command {

//    override fun key(): String = "configure-node"
    override fun key(): String = "test"

    override fun execute(): CliResult {
        val dbHost = System.getenv("POSTCHAIN_TEST_DB_HOST")
        val masterHost = System.getenv("POSTCHAIN_TEST_MASTER_HOST")
        println(
                """
                    db-host: $dbHost,
                    master-host: $masterHost
                """.trimIndent()
        )

        System.getenv().forEach { (k: String, v: String) -> println("$k:$v") }

        return Ok("Ok")
        //return CliError.NotImplemented(message = "configure-node command not implemented yet")
    }
}
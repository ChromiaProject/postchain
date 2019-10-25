package net.postchain

import net.postchain.api.rest.controller.HttpServer
import net.postchain.api.rest.controller.RestApi
import net.postchain.cli.Cli
import net.postchain.cli.CliError
import net.postchain.cli.Ok
import java.io.File
import java.lang.management.ManagementFactory

fun main(args: Array<String>) {
    RestApi(  "", HttpServer(8080, "", ""))
//    dumpPid()
//    val cliResult = Cli().parse(args)
//    when(cliResult){
//        is CliError -> {
//            when(cliResult) {
//                is CliError.MissingCommand -> {
//                    println(cliResult.message + "\n")
//                    Cli().usageCommands()
//                    println("\n")
//                }
//                is CliError.ArgumentNotFound -> {
//                    println(cliResult.message + "\n")
//                    Cli().usage(cliResult.command)
//                }
//                else -> cliResult.message?.let {
//                    println("\n$it\n")
//                }
//            }
//            System.exit(cliResult.code)
//        }
//        is Ok -> {
//            cliResult.info?.also {
//                println("\n$it\n")
//            }
//            if(!cliResult.isLongRunning){
//                System.exit(cliResult.code)
//            }
//        }
//    }
}

fun dumpPid() {
    val processName = ManagementFactory.getRuntimeMXBean().name
    val pid = processName.split("@")[0]
    File("postchain.pid").writeText(pid)
}

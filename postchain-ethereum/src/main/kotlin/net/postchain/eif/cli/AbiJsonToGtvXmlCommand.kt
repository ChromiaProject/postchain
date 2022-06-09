package net.postchain.eif.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.file
import java.io.File

class AbiJsonToGtvXmlCommand : CliktCommand(name = "generate-gtv-xml") {

    private val abiSource by option("-s", "--source", help = "Path to a JSON ABI file or a directory of JSON ABI files")
        .file(true)
        .required()

    private val eventNames by option("-e", "--events", help = "Names of the relevant events")
        .split(",")
        .required()

    private val outputFile by option("-o", "--o", help = "Output file path")
        .file()
        .default(File("events.xml"))

    override fun run() {
        val abiJson = if (abiSource.isDirectory) {
            abiSource.listFiles().joinToString(",", "[", "]") {
                it.readText().trim().trim('[', ']')
            }
        } else {
            abiSource.readText()
        }

        val gtvXml = AbiJsonToGtvXml.abiJsonToXml(abiJson, eventNames)

        println("Insert generated XML under key 'eif/events' in blockchain config")
        if (!outputFile.exists()) {
            outputFile.createNewFile()
        }
        outputFile.writeText(gtvXml)
    }
}

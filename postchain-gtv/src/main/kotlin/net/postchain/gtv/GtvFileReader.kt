package net.postchain.gtv

import net.postchain.gtv.gtvml.GtvMLParser
import java.io.File

object GtvFileReader {

    /**
     * Gets the entire content of GtvML (*.xml) or Gtv (*.gtv) files as a Gtv.
     * @param filename file name to read
     * @return the entire content of this file as a Gtv.
     */
    fun readFile(filename: String): Gtv = readFile(File(filename))

    /**
     * Gets the entire content of GtvML (*.xml) or Gtv (*.gtv) files as a Gtv.
     * @param file file to read
     * @return the entire content of this file as a Gtv.
     */
    fun readFile(file: File): Gtv {
        return when (file.name.takeLast(3)) {
            "xml" -> {
                GtvMLParser.parseGtvML(file.readText())
            }

            "gtv" -> {
                GtvFactory.decodeGtv(file.readBytes())
            }

            else -> throw IllegalArgumentException("Unknown file format of: ${file.absoluteFile}")
        }
    }
}

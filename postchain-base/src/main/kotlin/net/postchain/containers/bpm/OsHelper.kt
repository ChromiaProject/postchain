package net.postchain.containers.bpm

object OsHelper {

    /**
     * Returns true if current OS is Windows and false otherwise
     */
    fun isWindows(): Boolean {
        val os = System.getProperty("os.name")
        return os.contains("windows", true)
    }
}
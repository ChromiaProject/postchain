package net.postchain.base.data

import net.postchain.crypto.CryptoSystem

object DatabaseAccessFactory {

    const val POSTGRES_DRIVER_CLASS = "org.postgresql.Driver"

    fun createDatabaseAccess(cryptoSystem: CryptoSystem, driverClassName: String): DatabaseAccess {
        return when (driverClassName) {
            POSTGRES_DRIVER_CLASS -> PostgreSQLDatabaseAccess(cryptoSystem)
            else -> throw Exception("Unknown database driver class detected: $driverClassName")
        }
    }

}
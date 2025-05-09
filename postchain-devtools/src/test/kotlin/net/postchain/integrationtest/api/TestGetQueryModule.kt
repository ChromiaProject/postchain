package net.postchain.integrationtest.api

import mu.KLogging
import net.postchain.common.exception.UserMistake
import net.postchain.core.EContext
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvInteger
import net.postchain.gtx.SimpleGTXModule

class TestGetQueryModule : SimpleGTXModule<Unit>(Unit,
        mapOf(),
        mapOf(
                "test_query" to { _, _, args ->
                    val flag = (args as GtvDictionary)["flag"]!!.asBoolean()
                    val number = args["i"]!!.asInteger()
                    if (flag) {
                        GtvFactory.gtv(number * number)
                    } else {
                        GtvFactory.gtv(number)
                    }
                },
                "slow_query" to { _, ctxt, args ->
                    val seconds = (args as GtvDictionary)["seconds"]?.asInteger()
                            ?: throw UserMistake("No seconds property supplied")
                    Thread.sleep(seconds * 1000L)
                    GtvInteger(seconds)
                },
                "slow_db_query" to { _, ctxt, args ->
                    val seconds = (args as GtvDictionary)["seconds"]?.asInteger()
                            ?: throw UserMistake("No seconds property supplied")
                    ctxt.conn.prepareStatement("SELECT pg_sleep(?)").use {
                        it.setInt(1, seconds.toInt())
                        logger.info("Querying database for ${seconds}s...")
                        val resultSet = it.executeQuery()
                        logger.info("Querying completed")
                        resultSet.use { rs ->
                            rs.next()
                        }
                    }
                    GtvInteger(seconds)
                }
        )
) {
    companion object : KLogging()

    override fun initializeDB(ctx: EContext) {}
}
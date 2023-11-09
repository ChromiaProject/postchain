// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.controller

import com.google.gson.JsonElement
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.JsonPathException
import com.jayway.jsonpath.spi.json.GsonJsonProvider
import net.postchain.common.exception.UserMistake
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.NodeDiagnosticContext

interface DebugInfoQuery {

    /**
     * Returns string representation of [NodeDiagnosticContext] object converted to Json
     */
    fun queryDebugInfo(query: String?): JsonElement
}

private const val MAX_QUERY_LENGTH = 1024

class DefaultDebugInfoQuery(val nodeDiagnosticContext: NodeDiagnosticContext) : DebugInfoQuery {
    private val conf: Configuration = Configuration.ConfigurationBuilder().jsonProvider(GsonJsonProvider()).build()
    private val blockStatsQuery: JsonPath = JsonPath.compile("$.blockchain[*].['brid','block-statistics']")

    override fun queryDebugInfo(query: String?): JsonElement = when (query) {
        null -> default()
        DiagnosticProperty.BLOCK_STATS.prettyName -> blockStats()
        else -> jsonPath(query)
    }

    private fun default(): JsonElement {
        val json = nodeDiagnosticContext.format()
        json.asJsonObject.get(DiagnosticProperty.BLOCKCHAIN.prettyName)?.asJsonArray?.forEach {
            it.asJsonObject.remove(DiagnosticProperty.BLOCK_STATS.prettyName)
        }
        return json
    }

    private fun blockStats(): JsonElement = try {
        JsonPath.using(conf).parse(nodeDiagnosticContext.format()).read(blockStatsQuery)
    } catch (e: JsonPathException) {
        throw UserMistake(e.message ?: e.toString())
    }

    private fun jsonPath(query: String): JsonElement {
        if (query.length > MAX_QUERY_LENGTH) throw UserMistake("Query cannot be longer than $MAX_QUERY_LENGTH chars")
        return try {
            JsonPath.using(conf).parse(nodeDiagnosticContext.format()).read(query)
        } catch (e: JsonPathException) {
            throw UserMistake(e.message ?: e.toString())
        }
    }
}

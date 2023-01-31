// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.debug

import mu.KLogging
import java.util.Comparator.comparingInt

class DefaultNodeDiagnosticContext() : NodeDiagnosticContext {

    companion object : KLogging()

    private val properties: MutableMap<DiagnosticProperty, () -> Any?> = mutableMapOf()

    override fun addProperty(property: DiagnosticProperty, value: Any?) {
        properties[property] = { value }
    }

    override fun addProperty(property: DiagnosticProperty, lazyValue: () -> Any?) {
        properties[property] = lazyValue
    }

    override fun getProperty(property: DiagnosticProperty): (() -> Any?)? {
        return properties[property]
    }

    override fun getProperties(): Map<String, Any?> {
        return properties
                .toSortedMap(comparingInt(DiagnosticProperty::ordinal))
                .mapKeys { (k, _) -> k.prettyName }
                .mapValues { (k, v) ->
                    try {
                        v()
                    } catch (e: Exception) {
                        logger.warn(e) { "Unable to fetch diagnostic property $k: ${e.message}" }
                        "Unable to fetch value, ${e.message}"
                    }
                }
    }

    override fun removeProperty(property: DiagnosticProperty) {
        properties.remove(property)
    }
}
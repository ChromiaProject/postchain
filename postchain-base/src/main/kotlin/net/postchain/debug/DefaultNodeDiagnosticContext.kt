// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.debug

import java.util.Comparator.comparingInt

class DefaultNodeDiagnosticContext(override val enabled: Boolean) : NodeDiagnosticContext {

    private val properties: MutableMap<DiagnosticProperty, () -> Any?> = mutableMapOf()

    override fun addProperty(property: DiagnosticProperty, value: Any?) {
        if (enabled) {
            properties[property] = { value }
        }
    }

    override fun addProperty(property: DiagnosticProperty, lazyValue: () -> Any?) {
        if (enabled) {
            properties[property] = lazyValue
        }
    }

    override fun getProperty(property: DiagnosticProperty): (() -> Any?)? {
        return properties[property]
    }

    override fun getProperties(): Map<String, Any?> {
        return properties
                .toSortedMap(comparingInt(DiagnosticProperty::ordinal))
                .mapKeys { (k, _) -> k.prettyName }
                .mapValues { (_, v) -> v() }
    }

    override fun removeProperty(property: DiagnosticProperty) {
        properties.remove(property)
    }
}
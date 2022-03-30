// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.debug

interface NodeDiagnosticContext {

    /**
     * Indicates that [NodeDiagnosticContext] is enabled/disabled
     */
    val enabled: Boolean

    /**
     * Adds [DiagnosticProperty] property to the context
     */
    fun addProperty(property: DiagnosticProperty, value: Any?)

    /**
     * Adds lazy [DiagnosticProperty] property to the context
     */
    fun addProperty(property: DiagnosticProperty, lazyValue: () -> Any?)

    /**
     * Returns [DiagnosticProperty] property by key
     */
    fun getProperty(property: DiagnosticProperty): Any?

    /**
     * Returns properties key-value map where key is [DiagnosticProperty.prettyName]
     */
    fun getProperties(): Map<String, Any?>

    /**
     * Removes property by [DiagnosticProperty] key
     */
    fun removeProperty(property: DiagnosticProperty)
}


package net.postchain.debug

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.collection.IsMapContaining.hasEntry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import org.hamcrest.Matchers.`is` as Is

class DefaultNodeDiagnosticContextTest {

    @Test
    fun testEmptyContext() {
        // debug = true
        val sut = DefaultNodeDiagnosticContext(true)
        assert(sut.getProperties().isEmpty())

        // debug = false
        val sut2 = DefaultNodeDiagnosticContext(false)
        assert(sut2.getProperties().isEmpty())
    }

    @Test
    fun testAddPropertyToContextWithDebug() {
        val sut = DefaultNodeDiagnosticContext(true)
        sut.addProperty(DiagnosticProperty.VERSION, "4.4.4")
        sut.addProperty(DiagnosticProperty.PUB_KEY, "237823673673")
        sut.addProperty(DiagnosticProperty.CONTAINER_NAME) { "my-container" } // lazy

        // Asserts
        val content = sut.getProperties()
        assertThat(content, hasEntry(DiagnosticProperty.VERSION.prettyName, "4.4.4"))
        assertThat(content, hasEntry(DiagnosticProperty.PUB_KEY.prettyName, "237823673673"))
        assertThat(content, hasEntry(DiagnosticProperty.CONTAINER_NAME.prettyName, "my-container"))
    }

    @Test
    fun testAddPropertyToContextWithoutDebug() {
        val sut = DefaultNodeDiagnosticContext(false)
        sut.addProperty(DiagnosticProperty.VERSION, "4.4.4")
        sut.addProperty(DiagnosticProperty.PUB_KEY, "237823673673")
        sut.addProperty(DiagnosticProperty.CONTAINER_NAME) { "my-container" } // lazy

        // Asserts
        assert(sut.getProperties().isEmpty())
    }

    @Test
    fun testRemovePropertyOnContextWithDebug() {
        val sut = DefaultNodeDiagnosticContext(true)
        sut.addProperty(DiagnosticProperty.VERSION, "4.4.4")
        sut.addProperty(DiagnosticProperty.PUB_KEY, "237823673673")
        sut.addProperty(DiagnosticProperty.CONTAINER_NAME) { "my-container" } // lazy

        // Asserts
        assertThat(sut.getProperties().size, Is(equalTo(3)))

        // Remove
        sut.removeProperty(DiagnosticProperty.PUB_KEY)

        // Asserts
        val content = sut.getProperties()
        assertThat(content.size, Is(equalTo(2)))
        assertThat(content, hasEntry(DiagnosticProperty.VERSION.prettyName, "4.4.4"))
        assertThat(content, hasEntry(DiagnosticProperty.CONTAINER_NAME.prettyName, "my-container"))
    }

    @Test
    fun testRemovePropertyOnContextWithoutDebug() {
        val sut = DefaultNodeDiagnosticContext(false)
        sut.addProperty(DiagnosticProperty.VERSION, "4.4.4")
        sut.addProperty(DiagnosticProperty.PUB_KEY, "237823673673")
        sut.addProperty(DiagnosticProperty.CONTAINER_NAME) { "my-container" } // lazy

        // Asserts
        assert(sut.getProperties().isEmpty())

        // Remove
        sut.removeProperty(DiagnosticProperty.PUB_KEY)

        // Asserts
        assert(sut.getProperties().isEmpty())
    }

    @Test
    fun testGetPropertyForContextWithDebug() {
        val sut = DefaultNodeDiagnosticContext(true)
        sut.addProperty(DiagnosticProperty.VERSION, "4.4.4")
        sut.addProperty(DiagnosticProperty.CONTAINER_NAME) { "my-container" }

        // Asserts
        assertEquals(null, sut.getProperty(DiagnosticProperty.PUB_KEY)) // Unknown property
        assertEquals("4.4.4", sut.getProperty(DiagnosticProperty.VERSION)?.invoke())
        assertEquals("my-container", sut.getProperty(DiagnosticProperty.CONTAINER_NAME)?.invoke())
    }

    @Test
    fun testGetPropertyForContextWithoutDebug() {
        val sut = DefaultNodeDiagnosticContext(false)
        sut.addProperty(DiagnosticProperty.VERSION, "4.4.4")
        sut.addProperty(DiagnosticProperty.CONTAINER_NAME) { "my-container" }

        // Asserts
        assertEquals(null, sut.getProperty(DiagnosticProperty.PUB_KEY)) // Unknown property
        assertEquals(null, sut.getProperty(DiagnosticProperty.VERSION)?.invoke())
        assertEquals(null, sut.getProperty(DiagnosticProperty.CONTAINER_NAME)?.invoke())
    }

}
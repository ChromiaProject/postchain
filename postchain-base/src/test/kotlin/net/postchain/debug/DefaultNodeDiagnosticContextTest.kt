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
        val sut = DefaultNodeDiagnosticContext()
        assert(sut.getProperties().isEmpty())
    }

    @Test
    fun testAddPropertyToContext() {
        val sut = DefaultNodeDiagnosticContext()
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
    fun testRemovePropertyOnContext() {
        val sut = DefaultNodeDiagnosticContext()
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
    fun testGetPropertyForContext() {
        val sut = DefaultNodeDiagnosticContext()
        sut.addProperty(DiagnosticProperty.VERSION, "4.4.4")
        sut.addProperty(DiagnosticProperty.CONTAINER_NAME) { "my-container" }

        // Asserts
        assertEquals(null, sut.getProperty(DiagnosticProperty.PUB_KEY)) // Unknown property
        assertEquals("4.4.4", sut.getProperty(DiagnosticProperty.VERSION)?.invoke())
        assertEquals("my-container", sut.getProperty(DiagnosticProperty.CONTAINER_NAME)?.invoke())
    }

    @Test
    fun testExceptionWhenFetchingProperties() {
        val sut = DefaultNodeDiagnosticContext()
        sut.addProperty(DiagnosticProperty.VERSION, "4.4.4")
        sut.addProperty(DiagnosticProperty.CONTAINER_NAME) { throw Exception("fail") }

        // Asserts
        val content = sut.getProperties()
        assertThat(content, hasEntry(DiagnosticProperty.VERSION.prettyName, "4.4.4"))
        assertThat(content, hasEntry(DiagnosticProperty.CONTAINER_NAME.prettyName, "Unable to fetch value, fail"))
    }

}
package net.postchain

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

/**
 * A simple test class to demonstrate the testing process.
 */
class SimpleTest {

    /**
     * A basic test that demonstrates how to write a test in this project.
     */
    @Test
    fun testBasicFunctionality() {
        // Arrange
        val expected = 42
        
        // Act
        val actual = calculateAnswer()
        
        // Assert
        assertThat(actual).isEqualTo(expected)
    }
    
    /**
     * A simple function that returns the answer to the ultimate question.
     */
    private fun calculateAnswer(): Int {
        return 42
    }
}
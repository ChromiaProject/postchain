# Postchain Development Guidelines

This document provides guidelines and instructions for developing and testing the Postchain project.

## Build and Configuration Instructions

### Prerequisites

- Java Development Kit (JDK) 21
- Maven 3.x

### Building the Project

Postchain is a Maven-based project with multiple modules. To build the entire project, run:

```bash
mvn clean verify
```

To build without running tests:

```bash
mvn clean verify -DskipTests
```

### Project Structure

The project is organized into multiple modules:

- `postchain-common`: Common utilities and interfaces
- `postchain-gtv`: GTV (Generic Type Value) implementation
- `postchain-spi`: Service Provider Interface
- `postchain-base`: Core functionality
- `postchain-server`: Server implementation
- And several other specialized modules

## Testing Information

### Running Tests

Tests are written using JUnit 5. To run all tests:

```bash
mvn test
```

To run tests for a specific module:

```bash
mvn test -pl postchain-base
```

To run a specific test class:

```bash
mvn test -pl postchain-base -Dtest=SimpleTest
```

### Writing Tests

Tests should follow these guidelines:

1. Use JUnit 5 annotations (`@Test`, `@BeforeEach`, etc.)
2. Use assertk for assertions (`assertThat(actual).isEqualTo(expected)`)
3. Use mockito-kotlin for mocking
4. Follow the Arrange-Act-Assert pattern
5. Include proper documentation with Javadoc comments
6. Use descriptive test method names that explain what is being tested

### Example Test

Here's a simple example of a test class:

```kotlin
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
```

## Development Guidelines

### Code Style

The project uses the IntelliJ IDEA "obsolete" Kotlin code style, as specified in the pom.xml:

```xml
<kotlin.code.style>obsolete</kotlin.code.style>
```

### Kotlin Version

The project uses Kotlin 2.3.0 with JVM target 21:

```xml
<kotlin.version>2.3.0</kotlin.version>
<kotlin.compiler.jvmTarget>21</kotlin.compiler.jvmTarget>
```

### Logging

The project uses SLF4J with Log4j2 for logging. In Kotlin classes, use the KLogging companion object:

```kotlin
class MyClass {
    companion object : KLogging()

    fun doSomething() {
        logger.debug { "Debug message" }
        logger.info { "Info message" }
        logger.error { "Error message" }
    }
}
```

### Error Handling

The project defines custom exception types:

- `UserMistake`: For errors caused by incorrect user input
- `ProgrammerMistake`: For errors caused by programming errors

Use these exception types appropriately in your code.

## Additional Resources

- Project documentation is available in the `doc` directory
- API documentation can be generated using `mvn javadoc:javadoc`

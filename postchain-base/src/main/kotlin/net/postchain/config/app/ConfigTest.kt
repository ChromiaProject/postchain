package net.postchain.config.app

interface ConfigTest {
    /**
     * Tests the given application configuration.
     *
     * During the execution of the test, progress information, as well as errors and warnings, will be displayed to the user using `println` statements.
     *
     * @param appConfig the configuration to be tested.
     * @return an integer representing the result of the test.
     * - 0 indicates success
     * - Any other integer represent different types of failures or issues.
     */
    fun test(appConfig: AppConfig): Int
}
package net.postchain.ebft.worker

fun interface MessageProcessingLatch {

    /**
     * Awaits for permission to process messages while [exitCondition] returns false.
     * Once [exitCondition] returns true, exits with return value false.
     */
    fun awaitPermission(exitCondition: () -> Boolean): Boolean
}

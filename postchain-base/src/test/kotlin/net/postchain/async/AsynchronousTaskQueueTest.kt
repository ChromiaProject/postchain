package net.postchain.async

import assertk.assert
import assertk.assertions.containsExactly
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.Thread.sleep
import java.util.*

internal class AsynchronousTaskQueueTest {

    private lateinit var taskQueue: AsynchronousTaskQueue

    @BeforeEach
    fun setup() {
        taskQueue = AsynchronousTaskQueue()
    }

    @Test
    fun `Tasks are executed in order`() {
        // Given a slow and a fast task
        val resultList = Collections.synchronizedList(mutableListOf<String>())
        val slowTask = { sleep(100); resultList.add("slow") }
        val quickTask = { resultList.add("fast") }

        // When the slow task is executed before the fast one
        taskQueue.queueTask("slow") { slowTask() }
        val promise = taskQueue.queueTask("quick") { quickTask() }
        promise.get()

        // Then the slow one is finished before the fast one starts
        assert(resultList).containsExactly("slow", "fast")
    }

    @Test
    fun `Failed task does not affect next task`() {
        val failingTask = { throw IllegalArgumentException() }

        val failedPromise = taskQueue.queueTask("failing task") { failingTask() }
        val successfulPromise = taskQueue.queueTask("success") { true }

        assert(successfulPromise.get()).isTrue()
        assert(failedPromise.getError()).isInstanceOf(IllegalArgumentException::class)
    }

    @Test
    fun `Failed task will cancel second task`() {
        val failingTask = { sleep(10); throw IllegalArgumentException() }
        val dummyTask = { "Dummy task" }

        val failedPromise = taskQueue.queueTask("failing task") { failingTask() }

        val shouldFailPromise = taskQueue.queueTask("should fail", true) { dummyTask() }

        assert(shouldFailPromise.getError())
                .isInstanceOf(AsynchronousTaskQueue.PreviousTaskFailedException::class)
        assert(failedPromise.getError()).isInstanceOf(IllegalArgumentException::class)
    }

    @AfterEach
    fun breakdown() {
        taskQueue.shutdownQueue()
    }

}
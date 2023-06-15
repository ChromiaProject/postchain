package net.postchain.concurrent.util

import mu.withLoggingContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.function.BiConsumer

fun <T> CompletionStage<T>.get(): T = try {
    this.toCompletableFuture().get()
} catch (e: ExecutionException) {
    throw e.cause ?: e
}

fun <T> CompletionStage<T>.whenCompleteUnwrapped(loggingContext: Map<String, String> = mapOf(), consumer: BiConsumer<T, Throwable?>): CompletionStage<T> {
    return this.whenComplete { value, throwable ->
        val unwrapped: Throwable? = if (throwable is ExecutionException || throwable is CompletionException) {
            throwable.cause ?: throwable
        } else {
            throwable
        }
        withLoggingContext(loggingContext) {
            consumer.accept(value, unwrapped)
        }
    }
}

fun <T> CompletableFuture<T>.whenCompleteUnwrapped(loggingContext: Map<String, String>, consumer: BiConsumer<T, Throwable?>): CompletableFuture<T> {
    return this.whenComplete { value, throwable ->
        val unwrapped: Throwable? = if (throwable is ExecutionException || throwable is CompletionException) {
            throwable.cause ?: throwable
        } else {
            throwable
        }
        withLoggingContext(loggingContext) {
            consumer.accept(value, unwrapped)
        }
    }
}
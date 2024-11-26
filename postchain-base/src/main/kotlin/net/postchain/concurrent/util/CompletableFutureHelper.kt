package net.postchain.concurrent.util

import mu.withLoggingContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.function.BiConsumer
import java.util.function.Consumer

fun <T> CompletionStage<T>.get(): T = try {
    this.toCompletableFuture().get()
} catch (e: ExecutionException) {
    throw e.cause ?: e
}

fun <T> CompletionStage<T>.whenCompleteUnwrapped(
        loggingContext: Map<String, String> = mapOf(),
        onSuccess: Consumer<T>? = null,
        onError: Consumer<Throwable>? = null,
        always: BiConsumer<T?, Throwable?>? = null
): CompletionStage<T> {
    return this.whenComplete { value, throwable ->
        val unwrapped: Throwable? = if (throwable is ExecutionException || throwable is CompletionException) {
            throwable.cause ?: throwable
        } else {
            throwable
        }
        withLoggingContext(loggingContext) {
            if (unwrapped == null) {
                onSuccess?.accept(value)
            } else {
                onError?.accept(unwrapped)
            }
            always?.accept(value, unwrapped)
        }
    }
}

fun <T> CompletableFuture<T>.whenCompleteUnwrapped(
        loggingContext: Map<String, String> = mapOf(),
        onSuccess: Consumer<T>? = null,
        onError: Consumer<Throwable>? = null,
        always: BiConsumer<T?, Throwable?>? = null
): CompletableFuture<T> {
    return this.whenComplete { value, throwable ->
        val unwrapped: Throwable? = if (throwable is ExecutionException || throwable is CompletionException) {
            throwable.cause ?: throwable
        } else {
            throwable
        }
        withLoggingContext(loggingContext) {
            if (unwrapped == null) {
                onSuccess?.accept(value)
            } else {
                onError?.accept(unwrapped)
            }
            always?.accept(value, unwrapped)
        }
    }
}
package net.postchain.gtv.json

/** The result of a decode: either a value, or the fact that it was rejected. */
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Rejected(val type: String) : Outcome<Nothing>
}

/**
 * Runs [block], turning any throwable into [Outcome.Rejected].
 *
 * The exception *type* is recorded for the failure message but deliberately never compared: a codec is free to
 * reject the same input in its own way. What has to match across codecs is *whether* the input is rejected, and
 * what comes out when it is not.
 */
inline fun <T> outcome(block: () -> T): Outcome<T> =
    try {
        Outcome.Ok(block())
    } catch (t: Throwable) {
        Outcome.Rejected(t::class.simpleName ?: "Throwable")
    }

package com.alexey_anufriev.scopes_manager.support

import kotlin.time.Duration

/** Captures whether polling succeeded and the last transient error encountered. */
internal data class PollResult(
    val succeeded: Boolean,
    val lastError: Throwable?,
)

/** Repeatedly evaluates [condition] until it succeeds or [timeout] elapses. */
internal fun pollUntil(
    timeout: Duration,
    interval: Duration,
    condition: () -> Boolean,
): PollResult {
    val deadline = System.nanoTime() + timeout.inWholeNanoseconds
    var lastError: Throwable? = null

    while (System.nanoTime() < deadline) {
        try {
            if (condition()) {
                return PollResult(succeeded = true, lastError = lastError)
            }
        } catch (error: Throwable) {
            lastError = error
        }
        Thread.sleep(interval.inWholeMilliseconds)
    }

    return PollResult(succeeded = false, lastError = lastError)
}

/** Waits for [condition] and throws an assertion error describing the timeout. */
internal fun waitUntil(
    description: String,
    timeout: Duration,
    interval: Duration,
    condition: () -> Boolean,
) {
    val result = pollUntil(timeout, interval, condition)
    if (!result.succeeded) {
        throw AssertionError("Timed out waiting for $description", result.lastError)
    }
}

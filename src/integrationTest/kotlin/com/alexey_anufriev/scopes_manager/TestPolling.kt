package com.alexey_anufriev.scopes_manager

import kotlin.time.Duration

internal data class PollResult(
    val succeeded: Boolean,
    val lastError: Throwable?,
)

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

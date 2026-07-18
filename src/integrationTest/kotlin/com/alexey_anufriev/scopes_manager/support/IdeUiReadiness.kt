package com.alexey_anufriev.scopes_manager.support

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.getToolWindow
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.waitForIndicators
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Remote("com.intellij.openapi.project.DumbService")
private interface DumbServiceRef {
    fun isDumb(): Boolean
}

/** Waits for product-specific IDE startup work before UI assertions begin. */
internal class IdeUiReadiness(
    private val driver: Driver,
    private val log: (String) -> Unit,
) {
    /** Waits until the configured IDE is ready for UI interaction. */
    fun waitUntilReady(config: IdeTestConfig) {
        log("Waiting for UI readiness")
        when (config.product) {
            IdeProduct.RIDER -> waitForToolWindow(config.toolWindowId)
            IdeProduct.GOLAND -> {
                waitForToolWindow(config.toolWindowId)
                waitForSmartMode()
            }
            else -> {
                driver.waitForIndicators(10.minutes)
                driver.withContext(OnDispatcher.EDT) {
                    driver.getToolWindow(config.toolWindowId).show()
                }
            }
        }
        log("UI readiness completed")
    }

    private fun waitForToolWindow(toolWindowId: String) {
        log("Waiting for tool window")
        waitUntil(
            description = "tool window '$toolWindowId' to become available",
            timeout = 180.seconds,
            interval = 2.seconds,
        ) {
            driver.withContext(OnDispatcher.EDT) {
                driver.getToolWindow(toolWindowId).show()
            }
            true
        }
        log("Tool window ready")
    }

    private fun waitForSmartMode() {
        log("Waiting for smart mode")
        val dumbService = driver.service<DumbServiceRef>(driver.singleProject())
        waitUntil(
            description = "IDE smart mode",
            timeout = 180.seconds,
            interval = 500.milliseconds,
        ) {
            !dumbService.isDumb()
        }
        log("Smart mode ready")
    }
}

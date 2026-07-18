package com.alexey_anufriev.scopes_manager.driver

import com.alexey_anufriev.scopes_manager.support.pollUntil
import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.popupMenu
import com.intellij.driver.sdk.ui.ui
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Provides resilient interactions with IDE popup menus. */
internal class PopupDriver(
    private val driver: Driver,
    private val log: (String) -> Unit = ::logPopupCheckpoint,
) {
    /** Selects a popup item using speed search. */
    fun selectBySpeedSearch(itemText: String) {
        driver.ideFrame {
            keyboard {
                typeText(itemText)
                enter()
            }
        }
    }

    /** Waits until a popup containing [expectedText] appears. */
    fun containsText(expectedText: String, timeout: Duration = 3.seconds): Boolean {
        log("Waiting for popup text '$expectedText'")
        return pollUntil(timeout, 250.milliseconds) {
            containsTextNow(expectedText)
        }.succeeded.also { found ->
            if (found) {
                log("Popup text '$expectedText' found")
            }
        }
    }

    /** Opens [text] and verifies that its submenu contains [expectedChildText]. */
    fun openSubmenu(text: String, expectedChildText: String): Boolean {
        log("Finding menu item '$text'")
        val item = driver.ui.popupMenu().findMenuItemByText(text)

        item.moveMouse()
        if (containsText(expectedChildText, 2.seconds)) {
            return true
        }

        driver.ideFrame { keyboard { right() } }
        if (containsText(expectedChildText, 2.seconds)) {
            return true
        }

        item.click()
        if (containsText(expectedChildText, 2.seconds)) {
            return true
        }

        driver.ideFrame { keyboard { enter() } }
        return containsText(expectedChildText, 2.seconds)
    }

    /** Returns the visible popup item labels, or an empty list when no popup is available. */
    fun items(): List<String> = try {
        driver.ui.popupMenu().itemsList()
    } catch (_: Throwable) {
        emptyList()
    }

    /** Closes the current popup when one is present. */
    fun closeIfPresent() {
        try {
            log("Closing popup")
            driver.ideFrame {
                keyboard {
                    escape()
                    escape()
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun containsTextNow(expectedText: String): Boolean {
        try {
            if (driver.ui.popupMenu().itemsList().any { it.contains(expectedText) }) {
                return true
            }
        } catch (_: Throwable) {
        }

        val expectedTextXPath = "//div[" +
            "contains(@visible_text, '$expectedText') or " +
            "contains(@accessiblename, '$expectedText') or " +
            "contains(@javaclass, '$expectedText') or " +
            "contains(@text, '$expectedText')" +
            "]"
        return try {
            driver.ui.x(expectedTextXPath).present()
        } catch (_: Throwable) {
            false
        }
    }
}

/** Creates a popup driver bound to this IDE driver. */
internal fun Driver.popupDriver(): PopupDriver = PopupDriver(this)

private fun logPopupCheckpoint(message: String) {
    println("[integration-test:popup] $message")
}

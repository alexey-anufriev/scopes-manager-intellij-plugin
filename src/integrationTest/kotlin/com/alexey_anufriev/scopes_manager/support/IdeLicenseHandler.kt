package com.alexey_anufriev.scopes_manager.support

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.components.common.dialogs.LicenseDialogUi
import com.intellij.driver.sdk.ui.components.common.dialogs.licenseDialog
import com.intellij.driver.sdk.ui.components.elements.isDialogOpened
import com.intellij.driver.sdk.ui.ui
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Dismisses or activates the IDE license dialog when it appears during startup. */
internal class IdeLicenseHandler(
    private val driver: Driver,
    private val log: (String) -> Unit,
) {
    /** Handles the license dialog if it becomes visible within the startup window. */
    fun handleIfShown() {
        log("Checking license dialog")
        if (!waitForDialog()) {
            log("License dialog not shown")
            return
        }

        log("License dialog shown")
        driver.licenseDialog {
            if (continueButton.present() && continueButton.isEnabled()) {
                log("Clicking license Continue")
                continueButton.click()
                clickCloseIfPresent()
                return@licenseDialog
            }

            if (startTrialTab.present() && startTrialTab.isEnabled()) {
                log("Clicking license Start Trial tab")
                startTrialTab.click()
            }

            if (continueButton.present() && continueButton.isEnabled()) {
                log("Clicking license Continue")
                continueButton.click()
                clickCloseIfPresent()
                return@licenseDialog
            }

            if (startTrialButton.present() && startTrialButton.isEnabled()) {
                log("Clicking license Start Trial")
                startTrialButton.click()
                clickCloseIfPresent()
            }
        }
        log("License dialog handling completed")
    }

    private fun waitForDialog(): Boolean {
        log("Waiting for license dialog")
        return pollUntil(20.seconds, 250.milliseconds) {
            driver.ui.isDialogOpened("//div[@title='Manage Licenses']")
        }.succeeded.also { shown ->
            if (shown) {
                log("License dialog detected")
            }
        }
    }

    private fun LicenseDialogUi.clickCloseIfPresent() {
        log("Checking license Close button")
        pollUntil(5.seconds, 250.milliseconds) {
            val closeButton = x("//div[@accessiblename='Close']")
            if (closeButton.present() && closeButton.isEnabled()) {
                log("Clicking license Close")
                closeButton.click()
                true
            } else {
                false
            }
        }
    }
}

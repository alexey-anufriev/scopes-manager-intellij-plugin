package com.alexey_anufriev.scopes_manager.driver

import com.alexey_anufriev.scopes_manager.support.IdeTestConfig
import com.alexey_anufriev.scopes_manager.support.pollUntil
import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.components.common.ideFrame
import java.awt.event.KeyEvent
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Exercises the IDE's Add to Scope action through its supported UI entry points. */
internal class AddToScopeDriver(
    private val driver: Driver,
    private val projectView: ProjectViewDriver = driver.projectViewDriver(),
    private val popup: PopupDriver = driver.popupDriver(),
    private val log: (String) -> Unit = ::logAddToScopeCheckpoint,
) {
    /** Verifies that the Create New scope action is reachable for the configured sample file. */
    fun verifyAvailable(config: IdeTestConfig) {
        try {
            openFromContextMenu(config)
        } catch (contextMenuError: Throwable) {
            log("Context menu path failed; trying shortcut")
            if (!openWithShortcut(config)) {
                throw contextMenuError
            }
        }
    }

    private fun openFromContextMenu(config: IdeTestConfig) {
        var lastPopupItems = emptyList<String>()
        val result = pollUntil(30.seconds, 500.milliseconds) {
            var opened = false
            try {
                projectView.selectSampleFile(config.sampleFileNames, config.samplePath)
                projectView.openSampleFileContextMenu(config.sampleFileNames, config.samplePath)
                lastPopupItems = popup.items()

                opened = when {
                    popup.containsText(ADD_TO_SCOPE, 2.seconds) && openAddToScopeSubmenu() -> true
                    OTHER in lastPopupItems -> {
                        popup.openSubmenu(OTHER, ADD_TO_SCOPE) && openAddToScopeSubmenu()
                    }
                    else -> false
                }
                opened
            } finally {
                if (!opened) {
                    popup.closeIfPresent()
                }
            }
        }

        if (!result.succeeded) {
            throw AssertionError(
                "Add to Scope action popup did not contain '$CREATE_NEW'. Popup items: $lastPopupItems",
                result.lastError,
            )
        }
    }

    private fun openWithShortcut(config: IdeTestConfig): Boolean {
        projectView.selectSampleFile(config.sampleFileNames, config.samplePath)
        projectView.focus()
        driver.ideFrame {
            keyboard {
                hotKey(KeyEvent.VK_ALT, KeyEvent.VK_S)
            }
        }

        return popup.containsText(CREATE_NEW, 15.seconds).also { found ->
            if (!found) {
                popup.closeIfPresent()
            }
        }
    }

    private fun openAddToScopeSubmenu(): Boolean = popup.openSubmenu(ADD_TO_SCOPE, CREATE_NEW)

    companion object {
        private const val ADD_TO_SCOPE = "Add to Scope"
        private const val CREATE_NEW = "Create New..."
        private const val OTHER = "Other"
    }
}

/** Creates an Add to Scope driver bound to this IDE driver. */
internal fun Driver.addToScopeDriver(): AddToScopeDriver = AddToScopeDriver(this)

private fun logAddToScopeCheckpoint(message: String) {
    println("[integration-test:add-to-scope] $message")
}

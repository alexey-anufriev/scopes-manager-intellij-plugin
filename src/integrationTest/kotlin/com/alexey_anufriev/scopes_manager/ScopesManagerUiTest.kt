package com.alexey_anufriev.scopes_manager

import com.intellij.driver.client.Driver
import com.intellij.driver.model.TreePathToRow
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.toolwindows.ProjectViewToolWindowUi
import com.intellij.driver.sdk.ui.components.common.toolwindows.projectView
import com.intellij.driver.sdk.ui.components.elements.popupMenu
import com.intellij.driver.sdk.ui.ui
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ScopesManagerUiTest : UiIntegrationTestSupport() {

    @Test
    fun pluginStartsWithoutUiErrorsOnProjectOpen() {
        try {
            runUiTest { config ->
                handleLicenseDialogIfShown()
                waitForUiReady(config.productCode, config.toolWindowId)
                verifyProductBehavior(config)
            }
        } catch (throwable: Throwable) {
            val config = readConfig()
            skipUnavailableEap(config, throwable)
            throw throwable
        }
    }

    private fun Driver.verifyProductBehavior(config: UiTestConfig) {
        if (config.productCode == "RD") {
            verifyRiderProjectView(config)
            return
        }

        verifyAddToScopeAction(config)
    }

    private fun Driver.verifyRiderProjectView(config: UiTestConfig) {
        ideFrame {
            projectView {
                selectSampleFile(config.sampleFileNames, config.samplePath)
            }
        }
    }

    private fun Driver.verifyAddToScopeAction(config: UiTestConfig) {
        if (openAddToScopePopupWithShortcut(config)) {
            return
        }

        openAddToScopeMenu(config)
    }

    private fun Driver.openAddToScopePopupWithShortcut(config: UiTestConfig): Boolean {
        ideFrame {
            projectView {
                selectSampleFile(config.sampleFileNames, config.samplePath)
                focusProjectViewTree()
            }
            keyboard {
                hotKey(KeyEvent.VK_ALT, KeyEvent.VK_S)
            }
        }

        if (popupContainsText("Create New...", 15.seconds)) {
            return true
        }

        closePopupIfPresent()
        return false
    }

    private fun Driver.openAddToScopeMenu(config: UiTestConfig, timeout: Duration = 30.seconds) {
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        var lastPopupItems = emptyList<String>()
        var lastError: Throwable? = null

        while (System.nanoTime() < deadline) {
            try {
                ideFrame {
                    projectView {
                        selectSampleFile(config.sampleFileNames, config.samplePath)
                        rightClickSampleFile(config.sampleFileNames, config.samplePath)
                    }
                }

                if (popupContainsText("Add to Scope", 5.seconds)) {
                    val menu = ui.popupMenu()
                    lastPopupItems = menu.itemsList()
                    val addToScopeItem = menu.findMenuItemByText("Add to Scope")

                    addToScopeItem.moveMouse()
                    if (popupContainsText("Create New...", 5.seconds)) {
                        return
                    }

                    ideFrame {
                        keyboard {
                            right()
                        }
                    }
                    if (popupContainsText("Create New...", 5.seconds)) {
                        return
                    }

                    addToScopeItem.click()
                    if (popupContainsText("Create New...", 5.seconds)) {
                        return
                    }

                    ideFrame {
                        keyboard {
                            enter()
                        }
                    }
                    if (popupContainsText("Create New...", 5.seconds)) {
                        return
                    }
                } else {
                    lastPopupItems = popupItemsOrEmpty()
                }
            } catch (t: Throwable) {
                lastError = t
            }

            closePopupIfPresent()
            Thread.sleep(500)
        }

        throw AssertionError(
            "Add to Scope action popup did not contain 'Create New...'. Popup items: $lastPopupItems",
            lastError
        )
    }

    private fun Driver.popupContainsText(expectedText: String, timeout: Duration = 3.seconds): Boolean {
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        val expectedTextXPath = "//div[" +
            "contains(@visible_text, '$expectedText') or " +
            "contains(@accessiblename, '$expectedText') or " +
            "contains(@javaclass, '$expectedText') or " +
            "contains(@text, '$expectedText')" +
            "]"

        while (System.nanoTime() < deadline) {
            try {
                val popup = ui.popupMenu()
                val items = popup.itemsList()
                if (items.any { it.contains(expectedText, ignoreCase = false) }) {
                    return true
                }
            } catch (t: Throwable) {
            }

            try {
                val menuItem = ui.x(expectedTextXPath)
                if (menuItem.present()) {
                    return true
                }
            } catch (t: Throwable) {
            }

            Thread.sleep(250)
        }

        return false
    }

    private fun Driver.popupItemsOrEmpty(): List<String> {
        return try {
            ui.popupMenu().itemsList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun Driver.closePopupIfPresent() {
        try {
            ideFrame {
                keyboard {
                    escape()
                    escape()
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun ProjectViewToolWindowUi.selectSampleFile(
        sampleFileNames: Set<String>,
        samplePath: Array<String>
    ) {
        val deadline = System.nanoTime() + 60.seconds.inWholeNanoseconds
        var expandedPaths = projectViewTree.collectExpandedPaths()

        while (System.nanoTime() < deadline) {
            expandVisiblePath(samplePath)

            if (clickPath(samplePath)) {
                return
            }

            expandedPaths = projectViewTree.collectExpandedPaths()
            val row = expandedPaths.firstOrNull { path ->
                path.path.last() in sampleFileNames
            }?.row

            if (row != null && clickRow(row)) {
                return
            }

            expandProjectRoots(expandedPaths)
            Thread.sleep(500)
        }

        throw AssertionError(
            "Could not select sample file in Project View. Expanded paths: ${expandedPaths.map { it.path }}"
        )
    }

    private fun ProjectViewToolWindowUi.rightClickSampleFile(
        sampleFileNames: Set<String>,
        samplePath: Array<String>
    ) {
        focusProjectViewTree()

        if (rightClickPath(samplePath)) {
            return
        }

        val expandedPaths = projectViewTree.collectExpandedPaths()
        val row = expandedPaths.firstOrNull { path ->
            path.path.last() in sampleFileNames
        }?.row

        if (row != null && rightClickRow(row)) {
            return
        }

        throw AssertionError(
            "Could not right-click sample file in Project View. Expanded paths: ${expandedPaths.map { it.path }}"
        )
    }

    private fun ProjectViewToolWindowUi.focusProjectViewTree() {
        projectViewTree.setFocus()
    }

    private fun ProjectViewToolWindowUi.expandVisiblePath(path: Array<String>) {
        path.dropLast(1).forEachIndexed { index, segment ->
            val expandedPaths = projectViewTree.collectExpandedPaths()
            val currentRow = expandedPaths.firstOrNull { it.path.last() == segment }?.row ?: return
            val nextSegment = path[index + 1]
            if (expandedPaths.none { it.path.last() == nextSegment }) {
                projectViewTree.doubleClickRow(currentRow)
                Thread.sleep(300)
            }
        }
    }

    private fun ProjectViewToolWindowUi.expandProjectRoots(
        paths: List<TreePathToRow>
    ) {
        paths
            .filter { path -> path.path.size == 1 }
            .filterNot { path -> path.path.last() in setOf("External Libraries", "Scratches and Consoles") }
            .filterNot { root -> paths.any { path -> path.path.size > 1 && path.path.first() == root.path.first() } }
            .forEach { path ->
                try {
                    projectViewTree.doubleClickRow(path.row)
                } catch (_: Exception) {
                }
            }
    }

    private fun ProjectViewToolWindowUi.clickPath(path: Array<String>): Boolean {
        return try {
            projectViewTree.clickPath(*path, fullMatch = false)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun ProjectViewToolWindowUi.clickRow(row: Int): Boolean {
        return try {
            projectViewTree.clickRow(row)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun ProjectViewToolWindowUi.rightClickPath(path: Array<String>): Boolean {
        return try {
            projectViewTree.rightClickPath(*path, fullMatch = false)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun ProjectViewToolWindowUi.rightClickRow(row: Int): Boolean {
        return try {
            projectViewTree.rightClickRow(row)
            true
        } catch (_: Exception) {
            false
        }
    }

}

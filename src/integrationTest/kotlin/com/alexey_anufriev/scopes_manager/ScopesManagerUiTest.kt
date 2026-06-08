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

class ScopesManagerUiTest : IdeIntegrationTestSupport() {

    @Test
    fun brokenTest() {
        throw RuntimeException("Intentionally broken test")
    }

    @Test
    fun pluginStartsWithoutUiErrorsOnProjectOpen() {
        logTestCheckpoint("Scopes manager UI test started")
        try {
            runIdeIntegrationTest { config ->
                handleLicenseDialogIfShown()
                waitForUiReady(config.productCode, config.toolWindowId)
                logTestCheckpoint("Project loaded")
                verifyProductBehavior(config)
            }
        } catch (throwable: Throwable) {
            val config = readConfig()
            skipUnavailableEap(config, throwable)
            throw throwable
        }
    }

    private fun Driver.verifyProductBehavior(config: IdeTestConfig) {
        logTestCheckpoint("Verifying product behavior")
        if (config.productCode == "RD") {
            logTestCheckpoint("Verifying Rider project view")
            verifyRiderProjectView(config)
            return
        }

        logTestCheckpoint("Verifying Add to Scope action")
        verifyAddToScopeAction(config)
    }

    private fun Driver.verifyRiderProjectView(config: IdeTestConfig) {
        ideFrame {
            projectView {
                logTestCheckpoint("Selecting Rider sample file")
                selectSampleFile(config.sampleFileNames, config.samplePath)
                logTestCheckpoint("Rider sample file selected")
            }
        }
    }

    private fun Driver.verifyAddToScopeAction(config: IdeTestConfig) {
        try {
            logTestCheckpoint("Opening Add to Scope menu")
            openAddToScopeMenu(config)
            logTestCheckpoint("Add to Scope menu verified")
            return
        } catch (menuError: Throwable) {
            logTestCheckpoint("Add to Scope context menu path failed; trying shortcut")
            if (openAddToScopePopupWithShortcut(config)) {
                logTestCheckpoint("Add to Scope shortcut verified")
                return
            }

            throw menuError
        }
    }

    private fun Driver.openAddToScopePopupWithShortcut(config: IdeTestConfig): Boolean {
        ideFrame {
            projectView {
                logTestCheckpoint("Selecting sample file for shortcut")
                selectSampleFile(config.sampleFileNames, config.samplePath)
                logTestCheckpoint("Sample file selected for shortcut")
                logTestCheckpoint("Focusing Project View tree")
                focusProjectViewTree()
            }
            keyboard {
                logTestCheckpoint("Pressing Add to Scope shortcut")
                hotKey(KeyEvent.VK_ALT, KeyEvent.VK_S)
            }
        }

        logTestCheckpoint("Waiting for Create New menu item after shortcut")
        if (popupContainsText("Create New...", 15.seconds)) {
            logTestCheckpoint("Create New menu item found after shortcut")
            return true
        }

        logTestCheckpoint("Create New menu item not found after shortcut")
        closePopupIfPresent()
        return false
    }

    private fun Driver.openAddToScopeMenu(config: IdeTestConfig, timeout: Duration = 30.seconds) {
        logTestCheckpoint("Waiting for Add to Scope context menu")
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        var lastPopupItems = emptyList<String>()
        var lastError: Throwable? = null

        while (System.nanoTime() < deadline) {
            try {
                ideFrame {
                    projectView {
                        logTestCheckpoint("Selecting sample file")
                        selectSampleFile(config.sampleFileNames, config.samplePath)
                        logTestCheckpoint("Sample file selected")
                        logTestCheckpoint("Right-clicking sample file")
                        rightClickSampleFile(config.sampleFileNames, config.samplePath)
                        logTestCheckpoint("Sample file right-clicked")
                    }
                }

                lastPopupItems = popupItemsOrEmpty()
                logTestCheckpoint("Context menu opened")

                if (popupContainsText("Add to Scope", 2.seconds) && openAddToScopeSubmenu()) {
                    logTestCheckpoint("Add to Scope submenu opened")
                    return
                }

                if (lastPopupItems.any { it == "Other" }) {
                    logTestCheckpoint("Add to Scope menu not at top level; opening Other submenu")
                    if (openPopupSubmenu("Other", "Add to Scope") && openAddToScopeSubmenu()) {
                        logTestCheckpoint("Add to Scope submenu opened through Other")
                        return
                    }
                    lastPopupItems = lastPopupItems + listOf("Other -> ${popupItemsOrEmpty()}")
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

    private fun Driver.openAddToScopeSubmenu(): Boolean {
        logTestCheckpoint("Opening Add to Scope submenu")
        return openPopupSubmenu("Add to Scope", "Create New...")
    }

    private fun Driver.openPopupSubmenu(text: String, expectedChildText: String): Boolean {
        logTestCheckpoint("Finding menu item '$text'")
        val item = ui.popupMenu().findMenuItemByText(text)
        logTestCheckpoint("Menu item '$text' found")

        logTestCheckpoint("Moving mouse over menu item '$text'")
        item.moveMouse()
        if (popupContainsText(expectedChildText, 2.seconds)) {
            logTestCheckpoint("Menu item '$expectedChildText' found after hover")
            return true
        }

        ideFrame {
            keyboard {
                logTestCheckpoint("Pressing Right on menu item '$text'")
                right()
            }
        }
        if (popupContainsText(expectedChildText, 2.seconds)) {
            logTestCheckpoint("Menu item '$expectedChildText' found after Right")
            return true
        }

        logTestCheckpoint("Clicking menu item '$text'")
        item.click()
        if (popupContainsText(expectedChildText, 2.seconds)) {
            logTestCheckpoint("Menu item '$expectedChildText' found after click")
            return true
        }

        ideFrame {
            keyboard {
                logTestCheckpoint("Pressing Enter on menu item '$text'")
                enter()
            }
        }
        val found = popupContainsText(expectedChildText, 2.seconds)
        if (found) {
            logTestCheckpoint("Menu item '$expectedChildText' found after Enter")
        }
        return found
    }

    private fun Driver.popupContainsText(expectedText: String, timeout: Duration = 3.seconds): Boolean {
        logTestCheckpoint("Waiting for popup text '$expectedText'")
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
                    logTestCheckpoint("Popup text '$expectedText' found in items")
                    return true
                }
            } catch (t: Throwable) {
            }

            try {
                val menuItem = ui.x(expectedTextXPath)
                if (menuItem.present()) {
                    logTestCheckpoint("Popup text '$expectedText' found by XPath")
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
            logTestCheckpoint("Closing popup")
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
        logScopesManagerUiCheckpoint("Selecting sample file in Project View")
        val deadline = System.nanoTime() + 60.seconds.inWholeNanoseconds
        var expandedPaths = projectViewTree.collectExpandedPaths()

        while (System.nanoTime() < deadline) {
            expandVisiblePath(samplePath)

            if (clickPath(samplePath)) {
                logScopesManagerUiCheckpoint("Sample file selected by path")
                return
            }

            expandedPaths = projectViewTree.collectExpandedPaths()
            val row = expandedPaths.firstOrNull { path ->
                path.path.last() in sampleFileNames
            }?.row

            if (row != null && clickRow(row)) {
                logScopesManagerUiCheckpoint("Sample file selected by row")
                return
            }

            logScopesManagerUiCheckpoint("Expanding project roots")
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
        logScopesManagerUiCheckpoint("Right-clicking sample file in Project View")
        focusProjectViewTree()

        if (rightClickPath(samplePath)) {
            logScopesManagerUiCheckpoint("Sample file right-clicked by path")
            return
        }

        val expandedPaths = projectViewTree.collectExpandedPaths()
        val row = expandedPaths.firstOrNull { path ->
            path.path.last() in sampleFileNames
        }?.row

        if (row != null && rightClickRow(row)) {
            logScopesManagerUiCheckpoint("Sample file right-clicked by row")
            return
        }

        throw AssertionError(
            "Could not right-click sample file in Project View. Expanded paths: ${expandedPaths.map { it.path }}"
        )
    }

    private fun ProjectViewToolWindowUi.focusProjectViewTree() {
        logScopesManagerUiCheckpoint("Project View tree focused")
        projectViewTree.setFocus()
    }

    private fun ProjectViewToolWindowUi.expandVisiblePath(path: Array<String>) {
        path.dropLast(1).forEachIndexed { index, segment ->
            val expandedPaths = projectViewTree.collectExpandedPaths()
            val currentRow = expandedPaths.firstOrNull { it.path.last() == segment }?.row ?: return
            val nextSegment = path[index + 1]
            if (expandedPaths.none { it.path.last() == nextSegment }) {
                logScopesManagerUiCheckpoint("Expanding Project View path segment '$segment'")
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
                    logScopesManagerUiCheckpoint("Expanding Project View root '${path.path.last()}'")
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

private fun logScopesManagerUiCheckpoint(message: String) {
    println("[integration-test] $message")
}

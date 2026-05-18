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

        verifyPopupContainsAddToScope(config)
    }

    private fun Driver.verifyRiderProjectView(config: UiTestConfig) {
        ideFrame {
            projectView {
                selectSampleFile(config.sampleFileNames, config.samplePath)
            }
        }
    }

    private fun Driver.verifyPopupContainsAddToScope(config: UiTestConfig) {
        ideFrame {
            projectView {
                assertAddToScopeVisibleForSampleFile(config.sampleFileNames, config.samplePath)
            }
        }
        assertPopupContainsText("Add to Scope")
    }

    private fun Driver.assertPopupContainsText(expectedText: String, timeout: Duration = 15.seconds) {
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        var lastItems = emptyList<String>()
        var lastError: Throwable? = null
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
                lastItems = items
                if (items.any { it.contains(expectedText, ignoreCase = false) }) {
                    return
                }
            } catch (t: Throwable) {
                lastError = t
            }

            try {
                val menuItem = ui.x(expectedTextXPath)
                if (menuItem.present()) {
                    return
                }
            } catch (t: Throwable) {
                lastError = t
            }

            Thread.sleep(250)
        }

        throw AssertionError(
            "Popup did not contain '$expectedText'. Visible items: $lastItems",
            lastError
        )
    }

    private fun ProjectViewToolWindowUi.assertAddToScopeVisibleForSampleFile(
        sampleFileNames: Set<String>,
        samplePath: Array<String>
    ) {
        val deadline = System.nanoTime() + 60.seconds.inWholeNanoseconds
        var expandedPaths = projectViewTree.collectExpandedPaths()

        while (System.nanoTime() < deadline) {
            expandVisiblePath(samplePath)

            if (openContextMenuForPath(samplePath)) {
                return
            }

            expandedPaths = projectViewTree.collectExpandedPaths()
            val row = expandedPaths.firstOrNull { path ->
                path.path.last() in sampleFileNames
            }?.row

            if (row != null && (openContextMenuWithRightClick(row) || openContextMenuWithKeyboard(row))) {
                return
            }

            expandProjectRoots(expandedPaths)
            Thread.sleep(500)
        }

        throw AssertionError(
            "Could not find sample file in Project View. Expanded paths: ${expandedPaths.map { it.path }}"
        )
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
            .forEach { path ->
                try {
                    projectViewTree.doubleClickRow(path.row)
                } catch (_: Exception) {
                }
            }
    }

    private fun ProjectViewToolWindowUi.openContextMenuForPath(path: Array<String>): Boolean {
        if (isRiderTest()) {
            return try {
                projectViewTree.clickPath(*path, fullMatch = false)
                keyboard {
                    hotKey(KeyEvent.VK_SHIFT, KeyEvent.VK_F10)
                }
                true
            } catch (_: Exception) {
                try {
                    projectViewTree.rightClickPath(*path, fullMatch = false)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }

        return try {
            projectViewTree.rightClickPath(*path, fullMatch = false)
            true
        } catch (_: Exception) {
            try {
                projectViewTree.clickPath(*path, fullMatch = false)
                keyboard {
                    hotKey(KeyEvent.VK_SHIFT, KeyEvent.VK_F10)
                }
                true
            } catch (_: Exception) {
                false
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

    private fun ProjectViewToolWindowUi.openContextMenuWithRightClick(
        row: Int
    ): Boolean {
        if (isRiderTest()) {
            return false
        }

        return try {
            projectViewTree.rightClickRow(row)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun ProjectViewToolWindowUi.openContextMenuWithKeyboard(
        row: Int
    ): Boolean {
        return try {
            projectViewTree.clickRow(row)
            keyboard {
                hotKey(KeyEvent.VK_SHIFT, KeyEvent.VK_F10)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isRiderTest(): Boolean = System.getProperty("uiTestProductCode", "IC") == "RD"
}

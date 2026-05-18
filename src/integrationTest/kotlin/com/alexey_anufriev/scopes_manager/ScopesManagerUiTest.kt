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

        verifyAddToScopeShortcut(config)
    }

    private fun Driver.verifyRiderProjectView(config: UiTestConfig) {
        ideFrame {
            projectView {
                selectSampleFile(config.sampleFileNames, config.samplePath)
            }
        }
    }

    private fun Driver.verifyAddToScopeShortcut(config: UiTestConfig) {
        ideFrame {
            projectView {
                selectSampleFile(config.sampleFileNames, config.samplePath)
                keyboard {
                    hotKey(KeyEvent.VK_ALT, KeyEvent.VK_S)
                }
            }
        }

        if (!popupContainsText("Create New...", 15.seconds)) {
            throw AssertionError("Add to Scope shortcut popup did not contain 'Create New...'. Popup items: ${popupItemsOrEmpty()}")
        }
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

}

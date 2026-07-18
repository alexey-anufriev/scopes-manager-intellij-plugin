package com.alexey_anufriev.scopes_manager.driver

import com.alexey_anufriev.scopes_manager.support.pollUntil
import com.intellij.driver.client.Driver
import com.intellij.driver.model.TreePathToRow
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.toolwindows.ProjectViewToolWindowUi
import com.intellij.driver.sdk.ui.components.common.toolwindows.projectView
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Encapsulates Project tool-window navigation used by integration tests. */
internal class ProjectViewDriver(
    private val driver: Driver,
    private val log: (String) -> Unit = ::logProjectViewCheckpoint,
) {
    /** Selects the configured sample file in the Project tree. */
    fun selectSampleFile(sampleFileNames: Set<String>, samplePath: List<String>) {
        driver.ideFrame {
            projectView {
                selectSampleFileInTree(sampleFileNames, samplePath)
            }
        }
    }

    /** Opens the context menu for the configured sample file. */
    fun openSampleFileContextMenu(sampleFileNames: Set<String>, samplePath: List<String>) {
        driver.ideFrame {
            projectView {
                rightClickSampleFile(sampleFileNames, samplePath)
            }
        }
    }

    /** Moves keyboard focus to the Project tree. */
    fun focus() {
        driver.ideFrame {
            projectView {
                projectViewTree.setFocus()
            }
        }
    }

    private fun ProjectViewToolWindowUi.selectSampleFileInTree(
        sampleFileNames: Set<String>,
        samplePath: List<String>,
    ) {
        log("Selecting sample file in Project View")
        var expandedPaths = projectViewTree.collectExpandedPaths()
        val result = pollUntil(60.seconds, 500.milliseconds) {
            expandVisiblePath(samplePath)

            if (clickPath(samplePath)) {
                log("Sample file selected by path")
                return@pollUntil true
            }

            expandedPaths = projectViewTree.collectExpandedPaths()
            val row = expandedPaths.firstOrNull { it.path.last() in sampleFileNames }?.row
            if (row != null && clickRow(row)) {
                log("Sample file selected by row")
                return@pollUntil true
            }

            expandProjectRoots(expandedPaths)
            false
        }

        if (!result.succeeded) {
            throw AssertionError(
                "Could not select sample file in Project View. Expanded paths: ${expandedPaths.map { it.path }}",
                result.lastError,
            )
        }
    }

    private fun ProjectViewToolWindowUi.rightClickSampleFile(
        sampleFileNames: Set<String>,
        samplePath: List<String>,
    ) {
        log("Right-clicking sample file in Project View")
        projectViewTree.setFocus()

        if (rightClickPath(samplePath)) {
            log("Sample file right-clicked by path")
            return
        }

        val expandedPaths = projectViewTree.collectExpandedPaths()
        val row = expandedPaths.firstOrNull { it.path.last() in sampleFileNames }?.row
        if (row != null && rightClickRow(row)) {
            log("Sample file right-clicked by row")
            return
        }

        throw AssertionError(
            "Could not right-click sample file in Project View. Expanded paths: ${expandedPaths.map { it.path }}",
        )
    }

    private fun ProjectViewToolWindowUi.expandVisiblePath(path: List<String>) {
        path.dropLast(1).forEachIndexed { index, segment ->
            val expandedPaths = projectViewTree.collectExpandedPaths()
            val currentRow = expandedPaths.firstOrNull { it.path.last() == segment }?.row ?: return
            val nextSegment = path[index + 1]
            if (expandedPaths.none { it.path.last() == nextSegment }) {
                log("Expanding Project View path segment '$segment'")
                projectViewTree.doubleClickRow(currentRow)
                Thread.sleep(300)
            }
        }
    }

    private fun ProjectViewToolWindowUi.expandProjectRoots(paths: List<TreePathToRow>) {
        paths
            .filter { it.path.size == 1 }
            .filterNot { it.path.last() in IGNORED_ROOTS }
            .filterNot { root -> paths.any { it.path.size > 1 && it.path.first() == root.path.first() } }
            .forEach { path ->
                try {
                    log("Expanding Project View root '${path.path.last()}'")
                    projectViewTree.doubleClickRow(path.row)
                } catch (_: Exception) {
                }
            }
    }

    private fun ProjectViewToolWindowUi.clickPath(path: List<String>): Boolean = try {
        projectViewTree.clickPath(*path.toTypedArray(), fullMatch = false)
        true
    } catch (_: Exception) {
        false
    }

    private fun ProjectViewToolWindowUi.clickRow(row: Int): Boolean = try {
        projectViewTree.clickRow(row)
        true
    } catch (_: Exception) {
        false
    }

    private fun ProjectViewToolWindowUi.rightClickPath(path: List<String>): Boolean = try {
        projectViewTree.rightClickPath(*path.toTypedArray(), fullMatch = false)
        true
    } catch (_: Exception) {
        false
    }

    private fun ProjectViewToolWindowUi.rightClickRow(row: Int): Boolean = try {
        projectViewTree.rightClickRow(row)
        true
    } catch (_: Exception) {
        false
    }

    companion object {
        private val IGNORED_ROOTS = setOf("External Libraries", "Scratches and Consoles")
    }
}

/** Creates a Project View driver bound to this IDE driver. */
internal fun Driver.projectViewDriver(): ProjectViewDriver = ProjectViewDriver(this)

private fun logProjectViewCheckpoint(message: String) {
    println("[integration-test:project-view] $message")
}

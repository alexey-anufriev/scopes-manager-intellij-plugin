package com.alexey_anufriev.scopes_manager

import com.intellij.driver.client.Driver
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.getToolWindow
import com.intellij.driver.sdk.ui.components.common.dialogs.licenseDialog
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.toolwindows.ProjectViewToolWindowUi
import com.intellij.driver.sdk.ui.components.common.toolwindows.projectView
import com.intellij.driver.sdk.ui.components.elements.isDialogOpened
import com.intellij.driver.sdk.ui.components.elements.popupMenu
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.NoCIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import org.junit.jupiter.api.Test
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.awt.event.KeyEvent
import java.io.File
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ScopesManagerUiTest {

    init {
        di = DI {
            extend(di)
            bindSingleton<CIServer>(overrides = true) { failingCiServer() }
        }
    }

    @Test
    fun pluginStartsWithoutUiErrorsOnProjectOpen() {
        runUiTest { productCode, toolWindowId, sampleFileNames, samplePath, activateTrial ->
            if (activateTrial && productCode == "RD") {
                handleRiderLicenseDialogIfShown()
            }

            waitForUiReady(productCode, toolWindowId)
            ideFrame {
                projectView {
                    assertAddToScopeVisibleForSampleFile(sampleFileNames, samplePath)
                }
            }
            assertPopupContainsText("Add to Scope")
        }
    }

    private fun runUiTest(
        assertion: Driver.(
            productCode: String,
            toolWindowId: String,
            sampleFileNames: Set<String>,
            samplePath: Array<String>,
            activateTrial: Boolean
        ) -> Unit
    ) {
        val productCode = System.getProperty("uiTestProductCode", "IC")
        val ideVersion = System.getProperty("uiTestIdeVersion")
        val ideChannel = System.getProperty("uiTestIdeChannel", "release")
        val toolWindowId = System.getProperty("uiTestToolWindowId", "Project")
        val activateTrial = System.getProperty("uiTestActivateTrial", "false").toBoolean()
        val projectPath = System.getProperty(
            "uiTestProjectPath",
            "src/integrationTest/resources/test-projects/idea-project"
        )
        val sampleFileNames = System.getProperty("uiTestSampleFileNames", "App,App.java")
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        val samplePath = System.getProperty("uiTestSamplePath", "src/main/java/sample/App")
            .split('/')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toTypedArray()
        val projectHome = Path.of(projectPath)
        val trustedProjectPath = if (projectHome.toString().endsWith(".sln")) projectHome.parent else projectHome
        val testCase = TestCase(
            ideProduct(productCode),
            LocalProjectInfo(projectHome)
        )

        val ideUnderTest = when (ideChannel) {
            "eap" -> ideVersion?.let { testCase.useEAP(it) } ?: testCase.useEAP()
            else -> ideVersion?.let { testCase.withVersion(it) } ?: testCase.useRelease()
        }

        val testNameSuffix = listOf(productCode, ideVersion ?: ideChannel).joinToString("-")

        Starter.newContext(
            "scopes-manager-ui-smoke-$testNameSuffix",
            ideUnderTest
        ).apply {
            addProjectToTrustedLocations(trustedProjectPath)
            PluginConfigurator(this).installPluginFromFolder(File(System.getProperty("path.to.build.plugin")))
        }.runIdeWithDriver().useDriverAndCloseIde {
            assertion(productCode, toolWindowId, sampleFileNames, samplePath, activateTrial)
        }
    }

    private fun ideProduct(productCode: String): IdeInfo = when (productCode) {
        "IC" -> IdeProductProvider.IC
        "IU" -> IdeProductProvider.IU
        "GO" -> IdeProductProvider.GO
        "RD" -> IdeProductProvider.RD
        "WS" -> IdeProductProvider.WS
        else -> throw IllegalArgumentException("Unsupported IDE product code: $productCode")
    }

    private fun failingCiServer(): CIServer = object : CIServer by NoCIServer {
        override fun reportTestFailure(
            testName: String,
            message: String,
            details: String,
            linkToLogs: String?
        ) {
            if (shouldIgnoreKnownIdeFailure(message, details)) {
                return
            }
            throw AssertionError("$testName failed: $message\n$details")
        }
    }

    private fun shouldIgnoreKnownIdeFailure(message: String, details: String): Boolean {
        val productCode = System.getProperty("uiTestProductCode", "IC")
        return productCode == "RD" && listOf(message, details).any { it.contains("LicensingFacade is null") }
    }

    private fun Driver.waitForUiReady(productCode: String, toolWindowId: String) {
        if (productCode == "RD") {
            waitForToolWindow(toolWindowId, 90.seconds)
            return
        }

        waitForIndicators(5.minutes)
        withContext(OnDispatcher.EDT) {
            getToolWindow(toolWindowId).show()
        }
    }

    private fun Driver.waitForToolWindow(toolWindowId: String, timeout: Duration) {
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        var lastError: Throwable? = null

        while (System.nanoTime() < deadline) {
            try {
                withContext(OnDispatcher.EDT) {
                    getToolWindow(toolWindowId).show()
                }
                return
            } catch (t: Throwable) {
                lastError = t
                Thread.sleep(2.seconds.inWholeMilliseconds)
            }
        }

        throw AssertionError("Timed out waiting for tool window '$toolWindowId' to become available", lastError)
    }

    private fun Driver.handleRiderLicenseDialogIfShown() {
        if (!waitForLicenseDialog(5.seconds)) {
            return
        }

        licenseDialog {
            if (continueButton.present() && continueButton.isEnabled()) {
                continueButton.click()
                clickCloseIfPresent()
                return@licenseDialog
            }

            if (startTrialTab.present() && startTrialTab.isEnabled()) {
                startTrialTab.click()
            }

            if (continueButton.present() && continueButton.isEnabled()) {
                continueButton.click()
                clickCloseIfPresent()
                return@licenseDialog
            }

            if (startTrialButton.present() && startTrialButton.isEnabled()) {
                startTrialButton.click()
                clickCloseIfPresent()
                return@licenseDialog
            }
        }
    }

    private fun Driver.waitForLicenseDialog(timeout: Duration): Boolean {
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds

        while (System.nanoTime() < deadline) {
            if (ui.isDialogOpened("//div[@title='Manage Licenses']")) {
                return true
            }
            Thread.sleep(250)
        }

        return false
    }

    private fun Driver.assertPopupContainsText(expectedText: String, timeout: Duration = 15.seconds) {
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        var lastItems = emptyList<String>()
        var lastError: Throwable? = null

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
            Thread.sleep(250)
        }

        throw AssertionError(
            "Popup did not contain '$expectedText'. Visible items: $lastItems",
            lastError
        )
    }

    private fun com.intellij.driver.sdk.ui.components.common.dialogs.LicenseDialogUi.clickCloseIfPresent() {
        val deadline = System.nanoTime() + 5.seconds.inWholeNanoseconds

        while (System.nanoTime() < deadline) {
            val closeButton = x("//div[@accessiblename='Close']")
            if (closeButton.present() && closeButton.isEnabled()) {
                closeButton.click()
                return
            }
            Thread.sleep(250)
        }
    }

    private fun ProjectViewToolWindowUi.assertAddToScopeVisibleForSampleFile(
        sampleFileNames: Set<String>,
        samplePath: Array<String>
    ) {
        expandVisiblePath(samplePath)

        if (openContextMenuForPath(samplePath)) {
            return
        }

        val expandedPaths = projectViewTree.collectExpandedPaths()
        val row = expandedPaths.firstOrNull { path ->
            path.path.last() in sampleFileNames
        }?.row

        if (row != null && (openContextMenuWithRightClick(row) || openContextMenuWithKeyboard(row))) {
            return
        }

        throw AssertionError(
            "Could not find sample file in Project View. Expanded paths: ${expandedPaths.map { it.path }}"
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

    private fun ProjectViewToolWindowUi.openContextMenuForPath(path: Array<String>): Boolean {
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

    private fun ProjectViewToolWindowUi.openContextMenuWithRightClick(
        row: Int
    ): Boolean {
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
}

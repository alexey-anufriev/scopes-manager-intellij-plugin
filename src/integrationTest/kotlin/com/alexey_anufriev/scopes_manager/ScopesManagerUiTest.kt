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
import com.intellij.ide.starter.runner.SetupException
import org.junit.jupiter.api.Assumptions.assumeTrue
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

    private data class UiTestConfig(
        val productCode: String,
        val ideVersion: String?,
        val ideChannel: String,
        val toolWindowId: String,
        val activateTrial: Boolean,
        val projectHome: Path,
        val sampleFileNames: Set<String>,
        val samplePath: Array<String>
    ) {
        val testNameSuffix: String = listOf(productCode, ideVersion ?: ideChannel).joinToString("-")
    }

    init {
        di = DI {
            extend(di)
            bindSingleton<CIServer>(overrides = true) { failingCiServer() }
        }
    }

    @Test
    fun pluginStartsWithoutUiErrorsOnProjectOpen() {
        try {
            runUiTest { config ->
                if (config.activateTrial) {
                    handleLicenseDialogIfShown()
                }

                waitForUiReady(config.productCode, config.toolWindowId)
                verifyProductBehavior(config)
            }
        } catch (throwable: Throwable) {
            val config = readConfig()
            if (isUnavailableEap(config, throwable)) {
                assumeTrue(false, "Skipping EAP UI test for ${config.testNameSuffix}: JetBrains EAP build is currently unavailable or expired. Original error: ${throwable.message.orEmpty()}")
            }
            throw throwable
        }
    }

    private fun runUiTest(assertion: Driver.(UiTestConfig) -> Unit) {
        val config = readConfig()
        val testCase = TestCase(
            ideProduct(config.productCode),
            LocalProjectInfo(config.projectHome)
        )

        val ideUnderTest = when (config.ideChannel) {
            "eap" -> config.ideVersion?.let { testCase.useEAP(it) } ?: testCase.useEAP()
            else -> config.ideVersion?.let { testCase.withVersion(it) } ?: testCase.useRelease()
        }

        val context = Starter.newContext(
            "scopes-manager-ui-smoke-${config.testNameSuffix}",
            ideUnderTest
        ).apply {
            addProjectToTrustedLocations(config.projectHome)
            PluginConfigurator(this).installPluginFromFolder(File(System.getProperty("path.to.build.plugin")))
        }

        context.runIdeWithDriver().useDriverAndCloseIde {
            assertion(config)
        }
    }

    private fun isUnavailableEap(config: UiTestConfig, throwable: Throwable): Boolean {
        if (config.ideChannel != "eap") {
            return false
        }

        val message = throwable.message.orEmpty()
        return throwable is SetupException &&
                (message.contains("expired on") || message.contains("Failed to find specific release"))
    }

    private fun ideProduct(productCode: String): IdeInfo = when (productCode) {
        "IC" -> IdeProductProvider.IC
        "IU" -> IdeProductProvider.IU
        "GO" -> IdeProductProvider.GO
        "RD" -> IdeProductProvider.RD
        "WS" -> IdeProductProvider.WS
        else -> throw IllegalArgumentException("Unsupported IDE product code: $productCode")
    }

    private fun readConfig(): UiTestConfig {
        val projectPath = System.getProperty(
            "uiTestProjectPath",
            "src/integrationTest/resources/test-projects/idea-project"
        )

        return UiTestConfig(
            productCode = System.getProperty("uiTestProductCode", "IC"),
            ideVersion = System.getProperty("uiTestIdeVersion"),
            ideChannel = System.getProperty("uiTestIdeChannel", "release"),
            toolWindowId = System.getProperty("uiTestToolWindowId", "Project"),
            activateTrial = System.getProperty("uiTestActivateTrial", "false").toBoolean(),
            projectHome = Path.of(projectPath),
            sampleFileNames = System.getProperty("uiTestSampleFileNames", "App,App.java")
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet(),
            samplePath = System.getProperty("uiTestSamplePath", "src/main/java/sample/App")
                .split('/')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toTypedArray()
        )
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
        if (productCode != "RD") {
            return false
        }

        val ideFailureText = listOf(message, details)

        val knownRiderIssues = listOf(
            "LicensingFacade is null",
            "Empty menu item text for BackendEntityFrameworkActionGroupNew"
        )
        if (knownRiderIssues.any { issue -> ideFailureText.any { it.contains(issue) } }) {
            return true
        }

        val riderHostExitMessage = "Rider host (PID ="
        val riderHostExitCode = "unexpectedly exited with exit code 143"
        return ideFailureText.any { it.contains(riderHostExitMessage) } &&
            ideFailureText.any { it.contains(riderHostExitCode) }
    }

    private fun Driver.waitForUiReady(productCode: String, toolWindowId: String) {
        if (productCode == "RD" || productCode == "GO") {
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

    private fun Driver.handleLicenseDialogIfShown() {
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

    private fun ProjectViewToolWindowUi.selectSampleFile(
        sampleFileNames: Set<String>,
        samplePath: Array<String>
    ) {
        expandVisiblePath(samplePath)

        if (clickPath(samplePath)) {
            return
        }

        val expandedPaths = projectViewTree.collectExpandedPaths()
        val row = expandedPaths.firstOrNull { path ->
            path.path.last() in sampleFileNames
        }?.row

        if (row != null && clickRow(row)) {
            return
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

package com.alexey_anufriev.scopes_manager

import com.intellij.driver.client.Driver
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.getToolWindow
import com.intellij.driver.sdk.ui.components.common.dialogs.licenseDialog
import com.intellij.driver.sdk.ui.components.elements.isDialogOpened
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
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.io.File
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

abstract class UiIntegrationTestSupport {

    protected data class UiTestConfig(
        val productCode: String,
        val ideVersion: String?,
        val ideChannel: String,
        val toolWindowId: String,
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

    protected fun runUiTest(assertion: Driver.(UiTestConfig) -> Unit) {
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
            "${testContextName()}-${config.testNameSuffix}",
            ideUnderTest
        ).apply {
            addProjectToTrustedLocations(config.projectHome)
            PluginConfigurator(this).installPluginFromFolder(File(System.getProperty("path.to.build.plugin")))
        }

        context.runIdeWithDriver(runTimeout = 3.minutes).useDriverAndCloseIde {
            assertion(config)
        }
    }

    protected fun skipUnavailableEap(config: UiTestConfig, throwable: Throwable) {
        if (isUnavailableEap(config, throwable)) {
            assumeTrue(false, "Skipping EAP UI test for ${config.testNameSuffix}: JetBrains EAP build is currently unavailable or expired. Original error: ${throwable.message.orEmpty()}")
        }
    }

    protected fun readConfig(): UiTestConfig {
        val projectPath = System.getProperty(
            "uiTestProjectPath",
            "src/integrationTest/resources/test-projects/idea-project"
        )

        return UiTestConfig(
            productCode = System.getProperty("uiTestProductCode", "IC"),
            ideVersion = System.getProperty("uiTestIdeVersion"),
            ideChannel = System.getProperty("uiTestIdeChannel", "release"),
            toolWindowId = System.getProperty("uiTestToolWindowId", "Project"),
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

    protected fun Driver.waitForUiReady(productCode: String, toolWindowId: String) {
        if (productCode == "RD" || productCode == "GO") {
            waitForToolWindow(toolWindowId, 90.seconds)
            return
        }

        waitForIndicators(5.minutes)
        withContext(OnDispatcher.EDT) {
            getToolWindow(toolWindowId).show()
        }
    }

    protected fun Driver.handleLicenseDialogIfShown() {
        if (!waitForLicenseDialog(20.seconds)) {
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

    protected open fun testContextName(): String = "scopes-manager-ui"

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
        "PY" -> IdeProductProvider.PY
        "RD" -> IdeProductProvider.RD
        "RM" -> IdeProductProvider.RM
        "RR" -> IdeProductProvider.RR
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
}

package com.alexey_anufriev.scopes_manager

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.getToolWindow
import com.intellij.driver.sdk.singleProject
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
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

abstract class UiIntegrationTestSupport {

    @Remote("com.intellij.openapi.project.DumbService")
    interface DumbServiceRef {
        fun isDumb(): Boolean
    }

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
        logTestCheckpoint("UI test config read: ${config.testNameSuffix}")
        val testCase = TestCase(
            ideProduct(config.productCode),
            LocalProjectInfo(config.projectHome)
        )
        logTestCheckpoint("IDE test case created")

        val ideUnderTest = when (config.ideChannel) {
            "eap" -> config.ideVersion?.let { testCase.useEAP(it) } ?: testCase.useEAP()
            else -> config.ideVersion?.let { testCase.withVersion(it) } ?: testCase.useRelease()
        }
        logTestCheckpoint("IDE version selected")

        val context = Starter.newContext(
            "${testContextName()}-${config.testNameSuffix}",
            ideUnderTest
        ).apply {
            logTestCheckpoint("Adding trusted project location")
            addProjectToTrustedLocations(config.projectHome)
            logTestCheckpoint("Installing plugin into IDE test context")
            PluginConfigurator(this).installPluginFromDir(Path.of(System.getProperty("path.to.build.plugin")))
        }

        logTestCheckpoint("Starting IDE with driver")
        context.runIdeWithDriver(runTimeout = 3.minutes).useDriverAndCloseIde {
            logTestCheckpoint("Driver connected")
            assertion(config)
            logTestCheckpoint("Test assertions finished")
        }
        logTestCheckpoint("IDE closed")
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
        logTestCheckpoint("Waiting for UI readiness")
        if (productCode == "RD") {
            waitForToolWindow(toolWindowId, 180.seconds)
            logTestCheckpoint("UI readiness completed")
            return
        }

        if (productCode == "GO") {
            waitForToolWindow(toolWindowId, 180.seconds)
            waitForSmartMode(180.seconds)
            logTestCheckpoint("UI readiness completed")
            return
        }

        waitForIndicators(10.minutes)
        withContext(OnDispatcher.EDT) {
            getToolWindow(toolWindowId).show()
        }
        logTestCheckpoint("UI readiness completed")
    }

    protected fun Driver.handleLicenseDialogIfShown() {
        logTestCheckpoint("Checking license dialog")
        if (!waitForLicenseDialog(20.seconds)) {
            logTestCheckpoint("License dialog not shown")
            return
        }

        logTestCheckpoint("License dialog shown")
        licenseDialog {
            if (continueButton.present() && continueButton.isEnabled()) {
                logTestCheckpoint("Clicking license Continue")
                continueButton.click()
                clickCloseIfPresent()
                return@licenseDialog
            }

            if (startTrialTab.present() && startTrialTab.isEnabled()) {
                logTestCheckpoint("Clicking license Start Trial tab")
                startTrialTab.click()
            }

            if (continueButton.present() && continueButton.isEnabled()) {
                logTestCheckpoint("Clicking license Continue")
                continueButton.click()
                clickCloseIfPresent()
                return@licenseDialog
            }

            if (startTrialButton.present() && startTrialButton.isEnabled()) {
                logTestCheckpoint("Clicking license Start Trial")
                startTrialButton.click()
                clickCloseIfPresent()
                return@licenseDialog
            }
        }
        logTestCheckpoint("License dialog handling completed")
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
        "CL" -> IdeProductProvider.CL
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
        logTestCheckpoint("Waiting for tool window")
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        var lastError: Throwable? = null

        while (System.nanoTime() < deadline) {
            try {
                withContext(OnDispatcher.EDT) {
                    getToolWindow(toolWindowId).show()
                }
                logTestCheckpoint("Tool window ready")
                return
            } catch (t: Throwable) {
                lastError = t
                Thread.sleep(2.seconds.inWholeMilliseconds)
            }
        }

        throw AssertionError("Timed out waiting for tool window '$toolWindowId' to become available", lastError)
    }

    private fun Driver.waitForSmartMode(timeout: Duration) {
        logTestCheckpoint("Waiting for smart mode")
        val dumbService = service<DumbServiceRef>(singleProject())
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        var lastError: Throwable? = null

        while (System.nanoTime() < deadline) {
            try {
                if (!dumbService.isDumb()) {
                    logTestCheckpoint("Smart mode ready")
                    return
                }
            } catch (t: Throwable) {
                lastError = t
            }
            Thread.sleep(500)
        }

        throw AssertionError("Timed out waiting for IDE smart mode", lastError)
    }

    private fun Driver.waitForLicenseDialog(timeout: Duration): Boolean {
        logTestCheckpoint("Waiting for license dialog")
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds

        while (System.nanoTime() < deadline) {
            if (ui.isDialogOpened("//div[@title='Manage Licenses']")) {
                logTestCheckpoint("License dialog detected")
                return true
            }
            Thread.sleep(250)
        }

        return false
    }

    private fun com.intellij.driver.sdk.ui.components.common.dialogs.LicenseDialogUi.clickCloseIfPresent() {
        logTestCheckpoint("Checking license Close button")
        val deadline = System.nanoTime() + 5.seconds.inWholeNanoseconds

        while (System.nanoTime() < deadline) {
            val closeButton = x("//div[@accessiblename='Close']")
            if (closeButton.present() && closeButton.isEnabled()) {
                logTestCheckpoint("Clicking license Close")
                closeButton.click()
                return
            }
            Thread.sleep(250)
        }
    }

    protected fun logTestCheckpoint(message: String) {
        println("[integration-test] $message")
    }
}

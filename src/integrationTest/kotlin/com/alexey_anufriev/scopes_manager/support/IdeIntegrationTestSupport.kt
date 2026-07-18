package com.alexey_anufriev.scopes_manager.support

import com.intellij.driver.client.Driver
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
import kotlin.time.Duration.Companion.minutes

/** Starts an isolated IDE, prepares its UI, and exposes the connected driver to tests. */
abstract class IdeIntegrationTestSupport {
    private val config: IdeTestConfig by lazy { IdeTestConfigLoader.load() }

    init {
        di = DI {
            extend(di)
            bindSingleton<CIServer>(overrides = true) { failingCiServer(config.product) }
        }
    }

    /** Runs an IDE integration assertion after applying optional configuration [assumptions]. */
    protected fun ideTest(
        assumptions: IdeTestConfig.() -> Unit = {},
        assertion: Driver.(IdeTestConfig) -> Unit
    ) {
        config.assumptions()
        try {
            runIdeIntegrationTest(config) {
                IdeLicenseHandler(this) { message -> logTestCheckpoint(message) }.handleIfShown()
                IdeUiReadiness(this) { message -> logTestCheckpoint(message) }.waitUntilReady(config)
                assertion(config)
            }
        } catch (throwable: Throwable) {
            skipUnavailableEap(config, throwable)
            throw throwable
        }
    }

    private fun runIdeIntegrationTest(
        config: IdeTestConfig,
        assertion: Driver.() -> Unit
    ) {
        val testName = "${testContextName()}-${config.testNameSuffix}"
        logTestCheckpoint("Test '$testName' started")
        logTestCheckpoint("IDE test config read: ${config.testNameSuffix}")
        val testCase = TestCase(
            ideProduct(config.product),
            LocalProjectInfo(config.projectHome)
        )
        logTestCheckpoint("IDE test case created")

        val ideUnderTest = when (config.channel) {
            IdeChannel.EAP -> config.ideVersion?.let { testCase.useEAP(it) } ?: testCase.useEAP()
            IdeChannel.RELEASE -> config.ideVersion?.let { testCase.withVersion(it) } ?: testCase.useRelease()
        }
        logTestCheckpoint("IDE version selected")

        val context = Starter.newContext(
            testName,
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
            assertion()
            logTestCheckpoint("Test assertions finished")
        }
        logTestCheckpoint("IDE closed")
        logTestCheckpoint("Test '$testName' successful")
    }

    private fun skipUnavailableEap(config: IdeTestConfig, throwable: Throwable) {
        if (isUnavailableEap(config, throwable)) {
            assumeTrue(false, "Skipping EAP IDE test for ${config.testNameSuffix}: JetBrains EAP build is currently unavailable or expired. Original error: ${throwable.message.orEmpty()}")
        }
    }

    /** Returns the stable name used for the IDE Starter test context. */
    protected open fun testContextName(): String = "scopes-manager-ui"

    private fun isUnavailableEap(config: IdeTestConfig, throwable: Throwable): Boolean {
        if (config.channel != IdeChannel.EAP) {
            return false
        }

        val message = throwable.message.orEmpty()
        return throwable is SetupException &&
                (message.contains("expired on") || message.contains("Failed to find specific release"))
    }

    private fun ideProduct(product: IdeProduct): IdeInfo = when (product) {
        IdeProduct.CLION -> IdeProductProvider.CL
        IdeProduct.IDEA_COMMUNITY -> IdeProductProvider.IC
        IdeProduct.IDEA_ULTIMATE -> IdeProductProvider.IU
        IdeProduct.GOLAND -> IdeProductProvider.GO
        IdeProduct.PYCHARM -> IdeProductProvider.PY
        IdeProduct.RIDER -> IdeProductProvider.RD
        IdeProduct.RUBYMINE -> IdeProductProvider.RM
        IdeProduct.RUSTROVER -> IdeProductProvider.RR
        IdeProduct.WEBSTORM -> IdeProductProvider.WS
    }

    private fun failingCiServer(product: IdeProduct): CIServer = object : CIServer by NoCIServer {
        override fun reportTestFailure(
            testName: String,
            message: String,
            details: String,
            linkToLogs: String?
        ) {
            if (shouldIgnoreKnownIdeFailure(product, message, details)) {
                return
            }
            throw AssertionError("$testName failed: $message\n$details")
        }
    }

    private fun shouldIgnoreKnownIdeFailure(product: IdeProduct, message: String, details: String): Boolean {
        if (product != IdeProduct.RIDER) {
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

    /** Writes a consistently formatted integration-test checkpoint. */
    protected fun logTestCheckpoint(message: String) {
        println("[integration-test] $message")
    }
}

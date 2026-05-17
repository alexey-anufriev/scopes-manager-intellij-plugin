package com.alexey_anufriev.scopes_manager

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.ideFrame
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SwitchScopeUiTest : UiIntegrationTestSupport() {

    @Remote("com.intellij.psi.search.scope.packageSet.NamedScope")
    interface NamedScopeRef

    @Remote("com.intellij.psi.search.scope.packageSet.InvalidPackageSet")
    interface InvalidPackageSetRef

    @Remote("com.intellij.psi.search.scope.packageSet.NamedScopeManager")
    interface NamedScopeManagerRef {
        fun setScopes(scopes: Array<NamedScopeRef>)
        fun removeAllSets()
    }

    @Remote("com.intellij.ide.projectView.ProjectView")
    interface ProjectViewRef {
        fun getCurrentViewId(): String?
        fun getCurrentProjectViewPane(): ProjectViewPaneRef?
        fun getProjectViewPaneById(id: String): ProjectViewPaneRef?
    }

    @Remote("com.intellij.ide.projectView.impl.AbstractProjectViewPane")
    interface ProjectViewPaneRef {
        fun getSubId(): String?
        fun getSubIds(): Array<String>
        fun getPresentableSubIdName(subId: String): String
    }

    @Test
    fun switchScopeActionSwitchesProjectViewToSelectedScope() {
        val config = readConfig()
        assumeTrue(config.productCode != "RD", "IDE ${config.testNameSuffix} does not provide IntelliJ Scope View")

        try {
            runUiTest { uiConfig ->
                handleLicenseDialogIfShown()
                waitForUiReady(uiConfig.productCode, uiConfig.toolWindowId)

                val firstScope = "Scope A"
                val secondScope = "Scope B"
                withTemporaryLocalScopes(firstScope, secondScope) {
                    waitForScopeViewToContain(firstScope, secondScope)

                    invokeAction(SWITCH_SCOPE_ACTION_ID)
                    selectPopupItemWithSpeedSearch(secondScope)

                    waitForSelectedScope(secondScope)
                }
            }
        } catch (throwable: Throwable) {
            skipUnavailableEap(config, throwable)
            throw throwable
        }
    }

    override fun testContextName(): String = "scopes-manager-switch-scope-ui"

    private fun Driver.selectPopupItemWithSpeedSearch(itemText: String) {
        ideFrame {
            keyboard {
                typeText(itemText)
                enter()
            }
        }
    }

    private fun Driver.withTemporaryLocalScopes(vararg scopeNames: String, body: Driver.() -> Unit) {
        val project = singleProject()
        val localScopesManager = service<NamedScopeManagerRef>(project)

        try {
            val scopes = withContext(OnDispatcher.EDT) {
                scopeNames.map { scopeName -> newLocalScope(scopeName) }.toTypedArray()
            }
            withWriteAction {
                localScopesManager.setScopes(scopes)
            }
            body()
        } finally {
            withWriteAction {
                localScopesManager.removeAllSets()
            }
        }
    }

    private fun Driver.newLocalScope(scopeName: String): NamedScopeRef =
        new(
            NamedScopeRef::class,
            scopeName,
            new(InvalidPackageSetRef::class, "file:*")
        )

    private fun Driver.waitForScopeViewToContain(vararg scopeNames: String, timeout: Duration = 15.seconds) {
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        var lastScopeNames = emptyList<String>()
        var lastError: Throwable? = null

        while (System.nanoTime() < deadline) {
            try {
                lastScopeNames = scopeViewSubIds()
                    .mapNotNull { subId -> scopeViewPane()?.getPresentableSubIdName(subId) }
                if (lastScopeNames.containsAll(scopeNames.toList())) {
                    return
                }
            } catch (t: Throwable) {
                lastError = t
            }

            Thread.sleep(250)
        }

        throw AssertionError(
            "Scope View did not contain ${scopeNames.toList()}. Visible scopes: $lastScopeNames",
            lastError
        )
    }

    private fun Driver.waitForSelectedScope(scopeName: String, timeout: Duration = 15.seconds) {
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        var lastViewId: String? = null
        var lastSubId: String? = null
        var lastScopeName: String? = null
        var lastError: Throwable? = null

        while (System.nanoTime() < deadline) {
            try {
                withContext(OnDispatcher.EDT) {
                    val projectView = projectView()
                    val pane = projectView.getCurrentProjectViewPane()
                    lastViewId = projectView.getCurrentViewId()
                    lastSubId = pane?.getSubId()
                    lastScopeName = lastSubId?.let { subId -> pane?.getPresentableSubIdName(subId) }
                }

                if (lastViewId == SCOPE_VIEW_ID && lastScopeName == scopeName) {
                    return
                }
            } catch (t: Throwable) {
                lastError = t
            }

            Thread.sleep(250)
        }

        throw AssertionError(
            "Expected Project View to select scope '$scopeName', but current view was '$lastViewId', subId was '$lastSubId', scope was '$lastScopeName'",
            lastError
        )
    }

    private fun Driver.scopeViewSubIds(): List<String> = withContext(OnDispatcher.EDT) {
        scopeViewPane()?.getSubIds()?.toList().orEmpty()
    }

    private fun Driver.scopeViewPane(): ProjectViewPaneRef? =
        projectView().getProjectViewPaneById(SCOPE_VIEW_ID)

    private fun Driver.projectView(): ProjectViewRef =
        service(singleProject())

    companion object {
        private const val SWITCH_SCOPE_ACTION_ID = "com.alexey-anufriev.scopes-manager.SwitchScope"
        private const val SCOPE_VIEW_ID = "Scope"
    }
}

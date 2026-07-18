package com.alexey_anufriev.scopes_manager.driver

import com.alexey_anufriev.scopes_manager.support.pollUntil
import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.singleProject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Remote("com.intellij.ide.projectView.ProjectView")
private interface ProjectViewRef {
    fun getCurrentViewId(): String?
    fun getCurrentProjectViewPane(): ProjectViewPaneRef?
    fun getProjectViewPaneById(id: String): ProjectViewPaneRef?
}

@Remote("com.intellij.ide.projectView.impl.AbstractProjectViewPane")
private interface ProjectViewPaneRef {
    fun getSubId(): String?
    fun getSubIds(): Array<String>
    fun getPresentableSubIdName(subId: String): String
}

/** Reads and verifies the current state of the IDE's Scope view. */
internal class ScopeViewDriver(private val driver: Driver) {

    /** Waits until all [scopeNames] are visible in the Scope view. */
    fun waitUntilContains(vararg scopeNames: String, timeout: Duration = 15.seconds) {
        val expectedScopeNames = scopeNames.toList()
        waitUntil(
            timeout = timeout,
            condition = { snapshot -> snapshot.visibleScopeNames.containsAll(expectedScopeNames) },
            failureMessage = { snapshot ->
                "Scope View did not contain $expectedScopeNames. Visible scopes: ${snapshot.visibleScopeNames}"
            },
        )
    }

    /** Waits until [scopeName] is the selected Project View scope. */
    fun waitUntilSelected(scopeName: String, timeout: Duration = 15.seconds) {
        waitUntil(
            timeout = timeout,
            condition = { snapshot -> snapshot.viewId == SCOPE_VIEW_ID && snapshot.selectedScopeName == scopeName },
            failureMessage = { snapshot ->
                "Expected Project View to select scope '$scopeName', but current view was '${snapshot.viewId}', " +
                    "subId was '${snapshot.subId}', scope was '${snapshot.selectedScopeName}'"
            },
        )
    }

    private fun waitUntil(
        timeout: Duration,
        condition: (ScopeViewSnapshot) -> Boolean,
        failureMessage: (ScopeViewSnapshot) -> String,
    ) {
        var lastSnapshot = ScopeViewSnapshot.EMPTY
        val result = pollUntil(timeout, 250.milliseconds) {
            lastSnapshot = snapshot()
            condition(lastSnapshot)
        }
        if (!result.succeeded) {
            throw AssertionError(failureMessage(lastSnapshot), result.lastError)
        }
    }

    private fun snapshot(): ScopeViewSnapshot = driver.withContext(OnDispatcher.EDT) {
        val projectView = driver.service<ProjectViewRef>(driver.singleProject())
        val scopeViewPane = projectView.getProjectViewPaneById(SCOPE_VIEW_ID)
        val currentPane = projectView.getCurrentProjectViewPane()
        val currentSubId = currentPane?.getSubId()

        ScopeViewSnapshot(
            visibleScopeNames = scopeViewPane
                ?.getSubIds()
                ?.map(scopeViewPane::getPresentableSubIdName)
                .orEmpty(),
            viewId = projectView.getCurrentViewId(),
            subId = currentSubId,
            selectedScopeName = currentSubId?.let { currentPane.getPresentableSubIdName(it) },
        )
    }

    private data class ScopeViewSnapshot(
        val visibleScopeNames: List<String>,
        val viewId: String?,
        val subId: String?,
        val selectedScopeName: String?,
    ) {
        companion object {
            val EMPTY = ScopeViewSnapshot(
                visibleScopeNames = emptyList(),
                viewId = null,
                subId = null,
                selectedScopeName = null,
            )
        }
    }

    companion object {
        private const val SCOPE_VIEW_ID = "Scope"
    }
}

/** Creates a Scope View driver bound to this IDE driver. */
internal fun Driver.scopeViewDriver(): ScopeViewDriver = ScopeViewDriver(this)

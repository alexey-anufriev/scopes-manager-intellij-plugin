package com.alexey_anufriev.scopes_manager

import com.alexey_anufriev.scopes_manager.driver.popupDriver
import com.alexey_anufriev.scopes_manager.driver.scopeViewDriver
import com.alexey_anufriev.scopes_manager.fixture.withTemporaryLocalScopes
import com.alexey_anufriev.scopes_manager.support.IdeIntegrationTestSupport
import com.alexey_anufriev.scopes_manager.support.IdeProduct
import com.intellij.driver.sdk.invokeAction
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class SwitchScopeUiTest : IdeIntegrationTestSupport() {
    @Test
    fun switchScopeActionSwitchesProjectViewToSelectedScope() {
        ideTest(assumptions = {
            assumeTrue(product != IdeProduct.RIDER, "IDE $testNameSuffix does not provide IntelliJ Scope View")
        }) {
            val firstScope = "Scope A"
            val secondScope = "Scope B"
            withTemporaryLocalScopes(firstScope, secondScope) {
                val scopeView = scopeViewDriver()
                scopeView.waitUntilContains(firstScope, secondScope)

                invokeAction(SWITCH_SCOPE_ACTION_ID)
                popupDriver().selectBySpeedSearch(secondScope)

                scopeView.waitUntilSelected(secondScope)
            }
        }
    }

    override fun testContextName(): String = "scopes-manager-switch-scope-ui"

    companion object {
        private const val SWITCH_SCOPE_ACTION_ID = "com.alexey-anufriev.scopes-manager.SwitchScope"
    }
}

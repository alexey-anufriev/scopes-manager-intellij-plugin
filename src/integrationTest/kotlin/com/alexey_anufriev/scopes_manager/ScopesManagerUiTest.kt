package com.alexey_anufriev.scopes_manager

import com.alexey_anufriev.scopes_manager.driver.addToScopeDriver
import com.alexey_anufriev.scopes_manager.support.IdeIntegrationTestSupport
import org.junit.jupiter.api.Test

class ScopesManagerUiTest : IdeIntegrationTestSupport() {

    @Test
    fun pluginStartsWithoutUiErrorsOnProjectOpen() {
        ideTest { config ->
            addToScopeDriver().verifyAvailable(config)
        }
    }
}

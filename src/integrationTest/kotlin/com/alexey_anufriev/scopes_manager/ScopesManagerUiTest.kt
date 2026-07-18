package com.alexey_anufriev.scopes_manager

import com.alexey_anufriev.scopes_manager.driver.addToScopeDriver
import com.alexey_anufriev.scopes_manager.driver.projectViewDriver
import com.alexey_anufriev.scopes_manager.support.IdeIntegrationTestSupport
import com.alexey_anufriev.scopes_manager.support.IdeProduct
import org.junit.jupiter.api.Test

class ScopesManagerUiTest : IdeIntegrationTestSupport() {

    @Test
    fun pluginStartsWithoutUiErrorsOnProjectOpen() {
        ideTest { config ->
            if (config.product == IdeProduct.RIDER) {
                projectViewDriver().selectSampleFile(config.sampleFileNames, config.samplePath)
            } else {
                addToScopeDriver().verifyAvailable(config)
            }
        }
    }
}

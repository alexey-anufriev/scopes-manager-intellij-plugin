package com.alexey_anufriev.scopes_manager.actions.switch

import com.intellij.ide.scopeView.NamedScopeFilter
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.search.scope.packageSet.InvalidPackageSet
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class SwitchScopeActionTest {

    @Test
    fun `switch scope action should be available while indices are updating`() {
        assertThat(SwitchScopeAction()).isInstanceOf(DumbAware::class.java)
    }

    @Test
    fun `should combine local and shared editable scopes sorted alphabetically`() {
        val localScopesManager = holder(scope("Local B"), scope("Local A"))
        val sharedScopesManager = holder(scope("Shared A"))

        val actions = collectSwitchScopeActions(localScopesManager, sharedScopesManager)

        assertThat(actions.map { it.templatePresentation.text })
            .containsExactly("Local A", "Local B", "Shared A")
        assertThat(actions).allMatch { it is DumbAware }
    }

    @Test
    fun `should return empty array when both holders have no editable scopes`() {
        val actions = collectSwitchScopeActions(holder(), holder())

        assertThat(actions).isEmpty()
    }

    @Test
    fun `should return only local scopes when shared holder is empty`() {
        val localScopesManager = holder(scope("A"), scope("B"))
        val sharedScopesManager = holder()

        val actions = collectSwitchScopeActions(localScopesManager, sharedScopesManager)

        assertThat(actions.map { it.templatePresentation.text }).containsExactly("A", "B")
    }

    @Test
    fun `should return only shared scopes when local holder is empty`() {
        val localScopesManager = holder()
        val sharedScopesManager = holder(scope("A"), scope("B"))

        val actions = collectSwitchScopeActions(localScopesManager, sharedScopesManager)

        assertThat(actions.map { it.templatePresentation.text }).containsExactly("A", "B")
    }

    @Test
    fun `should not return scope actions when scope view pane is not available`() {
        val localScopesManager = holder(scope("A"))
        val sharedScopesManager = holder(scope("B"))

        val actions = collectSwitchScopeActions(localScopesManager, sharedScopesManager, scopeViewPaneAvailable = false)

        assertThat(actions).isEmpty()
    }

    @Test
    fun `should remove duplicates when local and shared scopes have same name`() {
        val localScopesManager = holder(scope("Duplicate"))
        val sharedScopesManager = holder(scope("Duplicate"))

        val actions = collectSwitchScopeActions(localScopesManager, sharedScopesManager)

        assertThat(actions.map { it.templatePresentation.text })
            .containsExactly("Duplicate")
    }

    @Test
    fun `should resolve scope view sub id from named scope filter`() {
        val targetScope = scope("Target")
        val otherFilter = NamedScopeFilter(holder(scope("Other")), scope("Other"))
        val targetFilter = NamedScopeFilter(holder(targetScope), targetScope)
        val filters = listOf(otherFilter, targetFilter)

        val subId = resolveScopeViewSubId(targetScope, filters)

        assertThat(subId).isEqualTo(targetFilter.toString())
        assertThat(subId).isNotEqualTo(targetScope.scopeId)
    }

    private fun scope(name: String): NamedScope =
        NamedScope(name, InvalidPackageSet(name))

    private fun holder(vararg scopes: NamedScope): NamedScopesHolder = mock {
        on { editableScopes } doReturn scopes
    }
}

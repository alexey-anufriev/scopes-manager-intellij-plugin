package com.alexey_anufriev.scopes_manager.actions.add

import com.alexey_anufriev.scopes_manager.utils.UiUtils.colorSelector
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.search.scope.packageSet.InvalidPackageSet
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.ui.ColorUtil
import com.intellij.ui.FileColorManager
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBUI
import java.awt.Color
import javax.swing.DefaultComboBoxModel

class CreateNewScopeDialog(
    private val event: AnActionEvent,
    defaultScopeName: String
) : DialogWrapper(event.project, true) {

    private val localScope = "Local"
    private val sharedScope = "Shared"
    private val dialogSize = JBUI.size(335, 245)

    private val localScopesManager: NamedScopeManager = NamedScopeManager.getInstance(event.project)

    private val sharedScopesManager: DependencyValidationManager =
        DependencyValidationManager.getInstance(event.project!!)

    private val availableScopes : List<String> =
        arrayOf(*localScopesManager.editableScopes, *sharedScopesManager.editableScopes).map { it.name }

    var scopeName = defaultScopeName
    var scopeType  = localScope
    var assignColor = true
    var assignedColor = Color.WHITE
    var includeOpenFiles = false

    init {
        title = "New Scope"
        isAutoAdjustable = false
        setSize(dialogSize.width(), dialogSize.height())
        init()
    }

    override fun createCenterPanel() = panel {
        row {
            cell {
                label("Scope Name")
            }
            cell {
                textField(::scopeName)
                    .focused()
                    .withValidationOnInput { validationScopeName(it) }
                    .withValidationOnApply { validationScopeName(it) }
            }
        }

        row {
            cell {
                label("Sharing Type")
            }
            cell {
                comboBox(DefaultComboBoxModel(arrayOf(localScope, sharedScope)), ::scopeType)
            }
        }

        row {
            val checkBox = checkBox("Assign Color", ::assignColor)
            cell {
                colorSelector(::assignedColor, checkBox.selected)
            }
        }

        row {
            cell(isFullWidth = true) {
                checkBox("Include all open files", ::includeOpenFiles)
            }
        }
    }

    override fun doOKAction() {
        super.doOKAction()

        val scopesManager = if (scopeType == localScope) {
            localScopesManager
        } else {
            sharedScopesManager
        }

        val newScope = NamedScope(scopeName, InvalidPackageSet(""))
        scopesManager.addScope(newScope)

        val project = event.project!!

        if (assignColor) {
            val hexColor = ColorUtil.toHex(assignedColor)
            val fileColorManager = FileColorManager.getInstance(project)
            fileColorManager.addScopeColor(scopeName, hexColor, scopeType == sharedScope)
        }

        val addToScopeAction = AddToScopeAction(scopesManager, newScope)
        var files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)!!

        if (includeOpenFiles) {
            val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
            files = files.plus(fileEditorManager.openFiles)
        }

        addToScopeAction.processFiles(project, files)
    }

    private fun ValidationInfoBuilder.validationScopeName(textField: JBTextField): ValidationInfo? {
        return when {
            textField.text.isBlank() -> error("Scope name cannot be empty")
            availableScopes.contains(textField.text) -> error("Scope with provided name already exist")
            else -> null
        }
    }

}

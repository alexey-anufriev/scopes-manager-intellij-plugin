package com.alexey_anufriev.scopes_manager.utils

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.ColorPanel
import com.intellij.ui.layout.Cell
import com.intellij.ui.layout.CellBuilder
import com.intellij.ui.layout.ComponentPredicate
import java.awt.Color
import java.util.Random
import kotlin.reflect.KMutableProperty0

object UiUtils {

    fun Cell.colorSelector(
        property: KMutableProperty0<Color>,
        condition: ComponentPredicate
    ) : CellBuilder<ColorPanel> {

        return component(ColorPanel().apply {
            selectedColor = getRandomColor()
            property.set(selectedColor!!)
            addActionListener { property.set((it.source as ColorPanel).selectedColor!!) }
        }).enableIf(condition)
    }

    private fun getRandomColor() : Color {
        val darkEditor = EditorColorsManager.getInstance().isDarkEditor
        val brightness = if (darkEditor) 0.4f else 1.0f
        return Color.getHSBColor(Random().nextFloat(), 0.2f, brightness)
    }

}

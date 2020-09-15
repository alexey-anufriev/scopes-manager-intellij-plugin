package com.alexey_anufriev.scopes_manager.utils

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
        return Color.getHSBColor(Random().nextFloat(), 0.2f, 1.0f)
    }

}

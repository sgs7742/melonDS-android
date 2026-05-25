package me.magnum.melonds.ui.common

import android.view.View
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import me.magnum.melonds.domain.model.LayoutComponent
import me.magnum.melonds.domain.model.Point
import me.magnum.melonds.domain.model.Rect

class LayoutComponentView(val view: View, val aspectRatio: Float, val component: LayoutComponent) {
    fun setPosition(position: Point) {
        view.updateLayoutParams<FrameLayout.LayoutParams> {
            leftMargin = position.x
            topMargin = position.y
        }
    }

    fun setSize(width: Int, height: Int) {
        view.updateLayoutParams {
            this.width = width
            this.height = height
        }
    }

    fun setPositionAndSize(position: Point, width: Int, height: Int) {
        view.updateLayoutParams<FrameLayout.LayoutParams> {
            this.width = width
            this.height = height
            leftMargin = position.x
            topMargin = position.y
        }
    }

    fun getPosition(): Point {
        val params = view.layoutParams as? FrameLayout.LayoutParams
        return if (params != null) {
            Point(params.leftMargin, params.topMargin)
        } else {
            Point(view.x.toInt(), view.y.toInt())
        }
    }

    fun getWidth(): Int {
        val params = view.layoutParams as? FrameLayout.LayoutParams
        return params?.width?.takeIf { it > 0 } ?: view.width
    }

    fun getHeight(): Int {
        val params = view.layoutParams as? FrameLayout.LayoutParams
        return params?.height?.takeIf { it > 0 } ?: view.height
    }

    fun getRect(): Rect {
        val params = view.layoutParams as? FrameLayout.LayoutParams
        return if (params != null) {
            Rect(params.leftMargin, params.topMargin, params.width, params.height)
        } else {
            Rect(view.x.toInt(), view.y.toInt(), view.width, view.height)
        }
    }
}
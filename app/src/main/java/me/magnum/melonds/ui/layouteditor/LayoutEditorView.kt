package me.magnum.melonds.ui.layouteditor

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.ui.common.LayoutComponentView
import me.magnum.melonds.ui.common.LayoutView
import kotlin.math.*

typealias ViewSelectedListener = ((LayoutComponentView, currentScale: Float, maxSize: Int, minSize: Int) -> Unit)

class LayoutEditorView(context: Context, attrs: AttributeSet?) : LayoutView(context, attrs) {
    companion object {
        const val POSITION_SIZE_STEP_PX = 10
    }

    enum class Anchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    data class SelectedViewScalingState(
            val constrainedSize: Int,
            val scale: Float,
            val maxSize: Int,
            val minSize: Int,
    )

    private var onViewSelectedListener: ViewSelectedListener? = null
    private var onViewDeselectedListener: ((LayoutComponentView) -> Unit)? = null
    private var otherClickListener: OnClickListener? = null
    private val defaultComponentWidth by lazy { screenUnitsConverter.dpToPixels(100f).toInt() }
    private val minComponentSize by lazy { screenUnitsConverter.dpToPixels(30f).toInt() }
    private var selectedView: LayoutComponentView? = null
    private var selectedViewAnchor = Anchor.TOP_LEFT

    init {
        super.setOnClickListener {
            if (selectedView != null) {
                deselectCurrentView()
            } else {
                otherClickListener?.onClick(it)
            }
        }
    }

    fun setOnViewSelectedListener(listener: ViewSelectedListener) {
        onViewSelectedListener = listener
    }

    fun setOnViewDeselectedListener(listener: (LayoutComponentView) -> Unit) {
        onViewDeselectedListener = listener
    }

    fun addLayoutComponent(component: LayoutComponent) {
        val componentBuilder = viewBuilderFactory.getLayoutComponentViewBuilder(component)
        val componentHeight = defaultComponentWidth / componentBuilder.getAspectRatio()
        val componentView = addPositionedLayoutComponent(PositionedLayoutComponent(Rect(0, 0, defaultComponentWidth, componentHeight.toInt()), component))
        views[component] = componentView
    }

    fun buildCurrentLayout(): UILayout {
        return UILayout(views.values.map { PositionedLayoutComponent(it.getRect(), it.component) })
    }

    override fun setOnClickListener(l: OnClickListener?) {
        otherClickListener = l
    }

    override fun onLayoutComponentViewAdded(layoutComponentView: LayoutComponentView) {
        super.onLayoutComponentViewAdded(layoutComponentView)
        setupDragHandler(layoutComponentView)
        layoutComponentView.view.alpha = 0.5f
    }

    private fun setupDragHandler(layoutComponentView: LayoutComponentView) {
        layoutComponentView.view.setOnTouchListener(object : OnTouchListener {
            private var dragging = false

            private var downOffsetX = -1f
            private var downOffsetY = -1f

            override fun onTouch(view: View?, motionEvent: MotionEvent?): Boolean {
                if (view == null)
                    return false

                if (selectedView != null) {
                    deselectCurrentView()
                }

                return when (motionEvent?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downOffsetX = motionEvent.x
                        downOffsetY = motionEvent.y
                        view.alpha = 1f
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!dragging) {
                            val distance = sqrt((motionEvent.x - downOffsetX).pow(2f) + (motionEvent.y - downOffsetY).pow(2f))
                            if (distance >= 25) {
                                dragging = true
                            }
                        } else {
                            dragView(layoutComponentView, motionEvent.x - downOffsetX, motionEvent.y - downOffsetY)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragging) {
                            selectView(layoutComponentView)
                        } else {
                            snapViewPositionToGrid(layoutComponentView)
                            view.alpha = 0.5f
                            dragging = false
                        }
                        true
                    }
                    else -> false
                }
            }
        })
    }

    private fun selectView(view: LayoutComponentView) {
        val anchorDistances = mutableMapOf<Anchor, Double>()
        anchorDistances[Anchor.TOP_LEFT] = view.getPosition().x.toDouble().pow(2) + view.getPosition().y.toDouble().pow(2)
        anchorDistances[Anchor.TOP_RIGHT] = (width - (view.getPosition().x + view.getWidth())).toDouble().pow(2) + view.getPosition().y.toDouble().pow(2)
        anchorDistances[Anchor.BOTTOM_LEFT] = view.getPosition().x.toDouble().pow(2) + (height - (view.getPosition().y + view.getHeight())).toDouble().pow(2)
        anchorDistances[Anchor.BOTTOM_RIGHT] = (width - (view.getPosition().x + view.getWidth())).toDouble().pow(2) + (height - (view.getPosition().y + view.getHeight())).toDouble().pow(2)

        var anchor = Anchor.TOP_LEFT
        var minDistance = Double.MAX_VALUE
        anchorDistances.keys.forEach {
            if (anchorDistances[it]!! < minDistance) {
                minDistance = anchorDistances[it]!!
                anchor = it
            }
        }

        selectedViewAnchor = anchor
        selectedView = view

        val layoutAspectRatio = width / height.toFloat()
        val selectedViewAspectRatio = view.aspectRatio
        val currentConstrainedDimension: Int
        val maxDimension: Int

        if (layoutAspectRatio > selectedViewAspectRatio) {
            maxDimension = height
            currentConstrainedDimension = view.getHeight()
        } else {
            maxDimension = width
            currentConstrainedDimension = view.getWidth()
        }

        val viewScale = (currentConstrainedDimension - minComponentSize) / (maxDimension - minComponentSize).toFloat()
        onViewSelectedListener?.invoke(view, viewScale, maxDimension, minComponentSize)
    }

    private fun deselectCurrentView() {
        selectedView?.let {
            it.view.alpha = 0.5f
            onViewDeselectedListener?.invoke(it)
        }
        selectedView = null
    }

    fun deleteSelectedView() {
        val currentlySelectedView = selectedView ?: return
        deselectCurrentView()
        removeView(currentlySelectedView.view)
        views.remove(currentlySelectedView.component)
    }

    private fun dragView(view: LayoutComponentView, offsetX: Float, offsetY: Float) {
        val currentPosition = view.getPosition()
        val finalX = min(max(currentPosition.x + offsetX, 0f), width - view.getWidth().toFloat())
        val finalY = min(max(currentPosition.y + offsetY, 0f), height - view.getHeight().toFloat())
        view.setPosition(Point(finalX.toInt(), finalY.toInt()))
    }

    fun nudgeSelectedViewPosition(deltaX: Int, deltaY: Int) {
        val view = selectedView ?: return
        val rect = view.getRect()
        val newX = snapToGrid(rect.x + deltaX).coerceIn(0, max(0, width - rect.width))
        val newY = snapToGrid(rect.y + deltaY).coerceIn(0, max(0, height - rect.height))
        view.setPosition(Point(newX, newY))
    }

    fun isSelectedViewScreen(): Boolean = selectedView?.component?.isScreen() == true

    fun centerSelectedViewHorizontally() {
        val view = selectedView ?: return
        if (!view.component.isScreen()) {
            return
        }
        val rect = view.getRect()
        val centeredX = snapToGrid((width - rect.width) / 2).coerceIn(0, max(0, width - rect.width))
        view.setPosition(Point(centeredX, rect.y))
    }

    fun centerSelectedViewVertically() {
        val view = selectedView ?: return
        if (!view.component.isScreen()) {
            return
        }
        val rect = view.getRect()
        val centeredY = snapToGrid((height - rect.height) / 2).coerceIn(0, max(0, height - rect.height))
        view.setPosition(Point(rect.x, centeredY))
    }

    fun nudgeSelectedViewSize(deltaPx: Int) {
        val view = selectedView ?: return
        val rect = view.getRect()
        val screenAspectRatio = width / height.toFloat()
        val selectedViewAspectRatio = view.aspectRatio
        val (newViewWidth, newViewHeight) = if (screenAspectRatio > selectedViewAspectRatio) {
            val newHeight = snapToGrid(rect.height + deltaPx).coerceIn(minComponentSize, height)
            Pair((newHeight * selectedViewAspectRatio).toInt(), newHeight)
        } else {
            val newWidth = snapToGrid(rect.width + deltaPx).coerceIn(minComponentSize, width)
            Pair(newWidth, (newWidth / selectedViewAspectRatio).toInt())
        }
        applySelectedViewSize(newViewWidth, newViewHeight)
    }

    fun getSelectedViewRect(): Rect? = selectedView?.getRect()

    fun getSelectedViewScalingState(): SelectedViewScalingState? {
        val view = selectedView ?: return null
        val rect = view.getRect()
        val screenAspectRatio = width / height.toFloat()
        val selectedViewAspectRatio = view.aspectRatio
        val maxDimension: Int
        val constrainedSize: Int

        if (screenAspectRatio > selectedViewAspectRatio) {
            maxDimension = height
            constrainedSize = rect.height
        } else {
            maxDimension = width
            constrainedSize = rect.width
        }

        val scale = (constrainedSize - minComponentSize) / (maxDimension - minComponentSize).toFloat()
        return SelectedViewScalingState(constrainedSize, scale, maxDimension, minComponentSize)
    }

    fun scaleSelectedView(newScale: Float) {
        val currentlySelectedView = selectedView ?: return

        val screenAspectRatio = width / height.toFloat()
        val selectedViewAspectRatio = currentlySelectedView.aspectRatio
        val newViewWidth: Int
        val newViewHeight: Int

        if (screenAspectRatio > selectedViewAspectRatio) {
            val scaledHeight = snapToGrid(((height - minComponentSize) * newScale + minComponentSize).roundToInt())
                    .coerceIn(minComponentSize, height)
            newViewWidth = (scaledHeight * selectedViewAspectRatio).toInt()
            newViewHeight = scaledHeight
        } else {
            val scaledWidth = snapToGrid(((width - minComponentSize) * newScale + minComponentSize).roundToInt())
                    .coerceIn(minComponentSize, width)
            newViewWidth = scaledWidth
            newViewHeight = (scaledWidth / selectedViewAspectRatio).toInt()
        }

        applySelectedViewSize(newViewWidth, newViewHeight)
    }

    private fun applySelectedViewSize(newViewWidth: Int, newViewHeight: Int) {
        val currentlySelectedView = selectedView ?: return
        val viewPosition = currentlySelectedView.getPosition()
        var viewX: Int
        var viewY: Int

        if (selectedViewAnchor == Anchor.TOP_LEFT) {
            viewX = viewPosition.x
            viewY = viewPosition.y
            if (viewX + newViewWidth > width) {
                viewX = width - newViewWidth
            }
            if (viewY + newViewHeight > height) {
                viewY = height - newViewHeight
            }
        } else if (selectedViewAnchor == Anchor.TOP_RIGHT) {
            viewX = viewPosition.x + currentlySelectedView.getWidth() - newViewWidth
            viewY = viewPosition.y
            if (viewX < 0) {
                viewX = 0
            }
            if (viewY + newViewHeight > height) {
                viewY = height - newViewHeight
            }
        } else if (selectedViewAnchor == Anchor.BOTTOM_LEFT) {
            viewX = viewPosition.x
            viewY = viewPosition.y + currentlySelectedView.getHeight() - newViewHeight
            if (viewX + newViewWidth > width) {
                viewX = width - newViewWidth
            }
            if (viewY < 0) {
                viewY = 0
            }
        } else {
            viewX = viewPosition.x + currentlySelectedView.getWidth() - newViewWidth
            viewY = viewPosition.y + currentlySelectedView.getHeight() - newViewHeight
            if (viewX < 0) {
                viewX = 0
            }
            if (viewY < 0) {
                viewY = 0
            }
        }
        currentlySelectedView.setPositionAndSize(Point(viewX, viewY), newViewWidth, newViewHeight)
    }

    private fun snapViewPositionToGrid(view: LayoutComponentView) {
        val rect = view.getRect()
        val snappedX = snapToGrid(rect.x).coerceIn(0, max(0, width - rect.width))
        val snappedY = snapToGrid(rect.y).coerceIn(0, max(0, height - rect.height))
        view.setPosition(Point(snappedX, snappedY))
    }

    private fun snapToGrid(value: Int): Int {
        return ((value.toFloat() / POSITION_SIZE_STEP_PX).roundToInt() * POSITION_SIZE_STEP_PX)
                .coerceAtLeast(0)
    }
}
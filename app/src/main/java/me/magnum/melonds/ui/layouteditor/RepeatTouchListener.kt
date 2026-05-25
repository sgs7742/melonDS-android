package me.magnum.melonds.ui.layouteditor

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

/**
 * Fires [onRepeat] once on press, then repeatedly while the view is held.
 */
class RepeatTouchListener(
        private val initialDelayMs: Long = 400,
        private val repeatIntervalMs: Long = 80,
        private val onRepeat: () -> Unit,
) : View.OnTouchListener {

    private val handler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.isPressed = true
                view.parent?.requestDisallowInterceptTouchEvent(true)
                onRepeat()
                val runnable = object : Runnable {
                    override fun run() {
                        onRepeat()
                        handler.postDelayed(this, repeatIntervalMs)
                    }
                }
                repeatRunnable = runnable
                handler.postDelayed(runnable, initialDelayMs)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                repeatRunnable?.let { handler.removeCallbacks(it) }
                repeatRunnable = null
                view.isPressed = false
                view.performClick()
                return true
            }
        }
        return false
    }
}

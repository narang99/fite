package dev.fiteduck.phoneagent

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

private const val IDLE_COLOR = 0xFF1F6FEB.toInt()
private const val LISTENING_COLOR = 0xFFD93025.toInt()
private const val CAPTION_COLOR = 0xCC202124.toInt()
private const val CAPTION_SECONDS = 6L

/** A round mic with a caption under it. Touching the mic is the hold, lifting the finger is the release. */
@SuppressLint("ViewConstructor")
class FloatingMicView(
    context: Context,
    private val onPress: () -> Unit,
    private val onRelease: () -> Unit,
) : LinearLayout(context) {
    private val mic = ImageView(context)
    private val caption = TextView(context)
    private val hideCaption = Runnable { caption.visibility = View.GONE }

    init {
        orientation = VERTICAL
        gravity = Gravity.END
        setUpMic()
        setUpCaption()
        addView(mic, LayoutParams(dp(64), dp(64)))
        addView(caption, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
    }

    fun show(status: Status) {
        mic.background = circle(if (status == Status.Listening) LISTENING_COLOR else IDLE_COLOR)
        showCaption(status)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setUpMic() {
        mic.setImageResource(R.drawable.ic_notification_mic)
        mic.setPadding(dp(16), dp(16), dp(16), dp(16))
        mic.background = circle(IDLE_COLOR)
        mic.elevation = dp(6).toFloat()
        mic.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { onPress(); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { onRelease(); true }
                else -> false
            }
        }
    }

    private fun setUpCaption() {
        caption.setTextColor(Color.WHITE)
        caption.textSize = 16f
        caption.maxWidth = dp(220)
        caption.setPadding(dp(12), dp(8), dp(12), dp(8))
        caption.background = GradientDrawable().apply {
            setColor(CAPTION_COLOR)
            cornerRadius = dp(12).toFloat()
        }
        caption.visibility = View.GONE
    }

    private fun showCaption(status: Status) {
        removeCallbacks(hideCaption)
        val message = status.message()
        caption.visibility = if (message.isEmpty()) View.GONE else View.VISIBLE
        caption.text = message
        if (status is Status.Heard || status is Status.Failed) postDelayed(hideCaption, CAPTION_SECONDS * 1000)
    }

    private fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

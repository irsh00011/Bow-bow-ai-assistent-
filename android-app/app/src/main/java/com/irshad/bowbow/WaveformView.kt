package com.irshad.bowbow

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

/** Animated voice waveform: 48 bars. While listening, the height
 *  follows the microphone level (from SpeechRecognizer RMS); idle,
 *  it shows a gentle resting wave. */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4D8DFF.toInt()
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
    }

    private val barCount = 48
    private var level = 0f // 0..1, from mic RMS
    private var animating = false
    private var phase = 0f

    private val ticker = object : Runnable {
        override fun run() {
            if (!animating) return
            phase += 0.35f
            invalidate()
            postDelayed(this, 50)
        }
    }

    /** Feed with SpeechRecognizer RMS (roughly 0..10). */
    fun setLevel(rmsDb: Float) {
        level = (rmsDb / 10f).coerceIn(0f, 1f)
        if (!animating) invalidate()
    }

    fun startAnimation() {
        if (animating) return
        animating = true
        post(ticker)
    }

    fun stopAnimation() {
        animating = false
        removeCallbacks(ticker)
        level = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val gap = w / barCount
        val centerY = h / 2f
        for (i in 0 until barCount) {
            val x = gap * i + gap / 2f
            val sine = (sin(phase + i * 0.55f) * 0.5f + 0.5f)
            val amp = if (animating) {
                0.15f + 0.85f * level * (0.35f + 0.65f * sine)
            } else {
                0.08f + 0.05f * sine
            }
            val barH = (h * amp).coerceAtLeast(6f)
            canvas.drawLine(x, centerY - barH / 2f, x, centerY + barH / 2f, paint)
        }
    }
}

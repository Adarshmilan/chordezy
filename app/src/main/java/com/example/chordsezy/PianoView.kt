package com.example.chordsezy

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** A two-octave keyboard (C to B) that highlights the notes of a chord. */
class PianoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Highlighted keys as semitones from the first C (0..23). */
    var highlighted: Set<Int> = emptySet()
        set(value) { field = value; invalidate() }

    var highlightColor: Int = context.getColor(R.color.major)
        set(value) { field = value; invalidate() }

    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEDEDF2.toInt() }
    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A1B22.toInt() }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A8DA0.toInt(); strokeWidth = 2f
    }
    private val hlPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private val whiteSemis = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private val blackSemis = intArrayOf(1, 3, -1, 6, 8, 10, -1) // after each white key

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * 0.22f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val whites = 14
        val kw = width / whites.toFloat()
        val h = height.toFloat()
        val r = kw * 0.15f
        hlPaint.color = highlightColor

        for (i in 0 until whites) {
            val semi = (i / 7) * 12 + whiteSemis[i % 7]
            rect.set(i * kw + 1, 0f, (i + 1) * kw - 1, h)
            canvas.drawRoundRect(rect, r, r, if (semi in highlighted) hlPaint else whitePaint)
            canvas.drawLine(i * kw, 0f, i * kw, h, linePaint)
        }
        val bw = kw * 0.6f
        val bh = h * 0.6f
        for (i in 0 until whites) {
            val b = blackSemis[i % 7]
            if (b < 0) continue
            val semi = (i / 7) * 12 + b
            val x = (i + 1) * kw - bw / 2
            rect.set(x, 0f, x + bw, bh)
            canvas.drawRoundRect(rect, r, r, if (semi in highlighted) hlPaint else blackPaint)
        }
    }
}

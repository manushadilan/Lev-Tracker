package com.example.leavetracker

import android.content.Context
import android.graphics.*
import android.view.View

// Bars = leave days per month
class ChartView(c: Context, private val monthly: DoubleArray, private val labels: List<String>, private val ink: Int, private val bar: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 22f }
    private fun f(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), 720)

    override fun onDraw(c: Canvas) {
        val max = maxOf(monthly.max(), 1.0)
        val l = 10f; val b = height - 50f; val t = 50f; val w = (width - 2 * l) / 12f
        p.color = ink; c.drawLine(l, b, width - l, b, p)
        for (i in 0..11) {
            val x = l + w * i + w / 2
            val hb = (monthly[i] / max * (b - t)).toFloat()
            p.color = bar; c.drawRect(x - w * 0.3f, b - hb, x + w * 0.3f, b, p)
            p.color = ink
            c.drawText(labels[i].take(3), x, b + 28f, p)
            if (monthly[i] > 0) c.drawText(f(monthly[i]), x, b - hb - 10f, p)
        }
    }
}

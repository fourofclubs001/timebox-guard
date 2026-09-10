package com.timebox.guard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * A dependency-free vertical bar chart. Feed it [setBars]; it draws evenly
 * spaced bars scaled to the largest value, with the value printed above each
 * bar and a short category label below. Used on the metrics dashboard for
 * the "focused time per day" chart.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    data class Bar(val label: String, val value: Float, val valueLabel: String)

    private var bars: List<Bar> = emptyList()

    var barColor: Int = Color.parseColor("#3F7DE0")
    var mutedColor: Int = Color.parseColor("#9AA0A6")

    private val density = resources.displayMetrics.density

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 1f * density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#666666")
        textSize = 11f * density
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        textSize = 11f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val rect = RectF()

    fun setBars(bars: List<Bar>) {
        this.bars = bars
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize((220 * density).toInt(), widthMeasureSpec)
        val height = resolveSize((170 * density).toInt(), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        if (bars.isEmpty()) return

        val topPad = 16f * density      // room for the value label
        val bottomPad = 18f * density   // room for the category label
        val chartTop = paddingTop + topPad
        val chartBottom = height - paddingBottom - bottomPad
        val chartLeft = paddingLeft.toFloat()
        val chartRight = width - paddingRight.toFloat()
        val chartHeight = max(1f, chartBottom - chartTop)

        canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, axisPaint)

        val maxValue = bars.maxOf { it.value }.coerceAtLeast(1f)
        val slot = (chartRight - chartLeft) / bars.size
        val barWidth = (slot * 0.55f).coerceAtMost(46f * density)

        bars.forEachIndexed { i, bar ->
            val cx = chartLeft + slot * i + slot / 2f
            val barHeight = if (bar.value <= 0f) 0f else chartHeight * (bar.value / maxValue)
            val top = chartBottom - barHeight

            if (barHeight > 0f) {
                barPaint.color = barColor
                rect.set(cx - barWidth / 2f, top, cx + barWidth / 2f, chartBottom)
                val r = 3f * density
                canvas.drawRoundRect(rect, r, r, barPaint)
                canvas.drawText(bar.valueLabel, cx, top - 5f * density, valuePaint)
            }

            canvas.drawText(bar.label, cx, height - paddingBottom - 4f * density, labelPaint)
        }
    }
}

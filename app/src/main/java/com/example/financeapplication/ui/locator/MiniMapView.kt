package com.example.financeapplication.ui.locator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.financeapplication.R
import com.example.financeapplication.domain.model.Branch
import com.example.financeapplication.domain.model.BranchType

/**
 * Lightweight schematic map (no Google Maps API key required). Pins are positioned with
 * normalised coordinates; replace with a real MapView when you add a Maps key.
 */
class MiniMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var onPinClick: ((String) -> Unit)? = null
    private var pins: List<Branch> = emptyList()
    private var selectedId: String? = null
    private val density = resources.displayMetrics.density

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.chip_bg) }
    private val road = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.surface); style = Paint.Style.STROKE; strokeWidth = 10 * density
        strokeCap = Paint.Cap.ROUND
    }
    private val park = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.success); alpha = 40 }
    private val pinFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pinDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.white) }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3 * density; color = ContextCompat.getColor(context, R.color.navy)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary); textSize = 12 * density; textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val clip = Path()

    fun setPins(list: List<Branch>, selected: String?) {
        pins = list
        selectedId = selected
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = 16 * density
        clip.reset(); clip.addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW)
        canvas.save(); canvas.clipPath(clip)
        canvas.drawRect(0f, 0f, w, h, bg)
        canvas.drawRoundRect(RectF(w * 0.05f, h * 0.05f, w * 0.30f, h * 0.45f), 12 * density, 12 * density, park)
        // roads
        canvas.drawLine(0f, h * 0.5f, w, h * 0.5f, road)
        canvas.drawLine(w * 0.35f, 0f, w * 0.35f, h, road)
        canvas.drawLine(w * 0.75f, 0f, w * 0.60f, h, road)
        canvas.drawLine(0f, h * 0.2f, w, h * 0.3f, road)
        canvas.drawLine(0f, h * 0.85f, w, h * 0.72f, road)
        for (p in pins) {
            val x = p.mapX * w
            val y = p.mapY * h
            val selected = p.id == selectedId
            val radius = (if (selected) 15 else 11) * density
            pinFill.color = ContextCompat.getColor(context, if (p.type == BranchType.BRANCH) R.color.accent_red else R.color.primary)
            canvas.drawCircle(x, y, radius, pinFill)
            canvas.drawCircle(x, y, radius * 0.38f, pinDot)
            if (selected) {
                canvas.drawCircle(x, y, radius + 3 * density, ring)
                canvas.drawText(p.name, x.coerceIn(60 * density, w - 60 * density), y - radius - 8 * density, label)
            }
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val hit = pins.minByOrNull { p -> dist(event.x, event.y, p.mapX * width, p.mapY * height) }
            if (hit != null && dist(event.x, event.y, hit.mapX * width, hit.mapY * height) < 30 * density) {
                onPinClick?.invoke(hit.id)
            }
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        Math.hypot((x1 - x2).toDouble(), (y1 - y2).toDouble()).toFloat()
}

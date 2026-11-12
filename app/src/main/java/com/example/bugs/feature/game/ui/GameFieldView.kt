package com.example.bugs.feature.game.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.example.bugs.R
import com.example.bugs.domain.game.Bug
import com.example.bugs.domain.game.BugType
import com.example.bugs.domain.game.GameRound
import com.example.bugs.domain.game.TiltBonus
import com.google.android.material.color.MaterialColors
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sin

class GameFieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val BONUS_ID = -2
    }

    var round: GameRound? = null
        set(value) {
            if (accessibleBonus != value?.tiltBonus) {
                accessibleBonus = value?.tiltBonus
                accessibility.invalidateRoot()
            }
            field = value
        }

    var active = false
        set(value) {
            if (field != value) accessibility.invalidateRoot()
            field = value
        }

    var onTap: ((Float, Float) -> Int?)? = null
    var onBonusTap: ((Float, Float) -> Boolean)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val background = MaterialColors.getColor(
        this,
        com.google.android.material.R.attr.colorSurfaceVariant
    )
    private val foreground = MaterialColors.getColor(
        this,
        com.google.android.material.R.attr.colorOnSurfaceVariant
    )

    private val colors = mapOf(
        "normal" to ContextCompat.getColor(context, R.color.bug_normal),
        "fast" to ContextCompat.getColor(context, R.color.bug_fast),
        "rare" to ContextCompat.getColor(context, R.color.bug_rare),
        "gold" to ContextCompat.getColor(context, R.color.bug_gold)
    )

    private val bodies = mapOf(
        "normal" to requireNotNull(AppCompatResources.getDrawable(context, R.drawable.bug_normal)).mutate(),
        "fast" to requireNotNull(AppCompatResources.getDrawable(context, R.drawable.bug_fast)).mutate(),
        "rare" to requireNotNull(AppCompatResources.getDrawable(context, R.drawable.bug_rare)).mutate(),
        "gold" to requireNotNull(AppCompatResources.getDrawable(context, R.drawable.bug_gold)).mutate()
    )

    private data class Effect(
        val x: Float,
        val y: Float,
        val points: Int,
        val born: Double,
        val bug: Bug?
    )

    private val effects = mutableListOf<Effect>()
    private var accessibleBonus: TiltBonus? = null
    private var gestureInField = false

    private val scale get() = min(width / GameRound.WIDTH, height / GameRound.HEIGHT)
    private val left get() = (width - GameRound.WIDTH * scale) / 2
    private val top get() = (height - GameRound.HEIGHT * scale) / 2

    private val accessibility = object : ExploreByTouchHelper(this) {

        override fun getVirtualViewAt(x: Float, y: Float): Int {
            if (!active || scale <= 0) return INVALID_ID

            if (round?.tiltBonus?.contains((x - left) / scale, (y - top) / scale) == true) return BONUS_ID

            return round?.bugs
                ?.lastOrNull { it.contains((x - left) / scale, (y - top) / scale) }
                ?.id
                ?.toInt()
                ?: INVALID_ID
        }

        override fun getVisibleVirtualViews(ids: MutableList<Int>) {
            if (active) {
                round?.bugs?.forEach { ids.add(it.id.toInt()) }
                if (round?.tiltBonus != null) ids.add(BONUS_ID)
            }
        }

        override fun onPopulateNodeForVirtualView(id: Int, node: AccessibilityNodeInfoCompat) {
            if (id == BONUS_ID) {
                val bonus = round?.tiltBonus
                node.contentDescription = context.getString(
                    if (bonus == null) R.string.game_bonus_gone else R.string.game_tilt_bonus_accessibility
                )
                node.setBoundsInParent(
                    if (bonus == null) {
                        Rect(0, 0, 1, 1)
                    } else {
                        Rect(
                            (left + (bonus.x - TiltBonus.RADIUS) * scale).toInt(),
                            (top + (bonus.y - TiltBonus.RADIUS) * scale).toInt(),
                            (left + (bonus.x + TiltBonus.RADIUS) * scale).toInt(),
                            (top + (bonus.y + TiltBonus.RADIUS) * scale).toInt()
                        )
                    }
                )
                node.isClickable = active && bonus != null
                node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)

                return
            }

            val bug = round?.bugs?.find { it.id.toInt() == id }

            node.contentDescription = if (bug == null) {
                context.getString(R.string.game_gone)
            } else {
                context.getString(
                    if (bug.type == BugType.GOLD) R.string.game_gold_accessibility else R.string.game_bug_accessibility,
                    bug.points
                )
            }
            node.setBoundsInParent(
                if (bug == null) {
                    Rect(0, 0, 1, 1)
                } else {
                    Rect(
                        (left + (bug.x - bug.size * .8f) * scale).toInt(),
                        (top + (bug.y - bug.size * .8f) * scale).toInt(),
                        (left + (bug.x + bug.size * .8f) * scale).toInt(),
                        (top + (bug.y + bug.size * .8f) * scale).toInt()
                    )
                }
            )

            node.isClickable = active && bug != null
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        }

        override fun onPerformActionForVirtualView(
            id: Int,
            action: Int,
            arguments: Bundle?
        ): Boolean {
            if (!active || action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false

            if (id == BONUS_ID) {
                val bonus = round?.tiltBonus ?: return false
                tap(bonus.x, bonus.y)
            } else {
                val bug = round?.bugs?.find { it.id.toInt() == id } ?: return false
                tap(bug.x, bug.y)
            }
            sendEventForVirtualView(id, AccessibilityEvent.TYPE_VIEW_CLICKED)

            return true
        }
    }

    init {
        isClickable = true
        isFocusable = true
        ViewCompat.setAccessibilityDelegate(this, accessibility)
    }

    fun clearEffects() {
        effects.clear()
        accessibility.invalidateRoot()
    }

    override fun dispatchHoverEvent(event: MotionEvent) =
        accessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (scale <= 0) return

        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)

        paint.color = background
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(0f, 0f, GameRound.WIDTH, GameRound.HEIGHT, 40f, 40f, paint)
        canvas.clipRect(0f, 0f, GameRound.WIDTH, GameRound.HEIGHT)

        paint.color = foreground
        paint.alpha = 18
        for (x in 80..920 step 120) {
            for (y in 80..1320 step 120) {
                canvas.drawCircle(x.toFloat(), y.toFloat(), 2f, paint)
            }
        }
        paint.alpha = 255

        val game = round
        game?.bugs?.forEach { drawBug(canvas, it, game.elapsed, 1f, 1f) }

        effects.removeAll { (game?.elapsed ?: 0.0) - it.born >= .45 }
        effects.forEach { effect ->
            val age = ((game?.elapsed ?: effect.born) - effect.born).toFloat()
            val progress = (age / .45f).coerceIn(0f, 1f)

            effect.bug?.let { drawBug(canvas, it, effect.born, 1 - progress, 1 - progress * .4f) }

            paint.color = foreground
            paint.alpha = ((1 - progress) * 255).toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawCircle(effect.x, effect.y, 16 + progress * 55, paint)

            paint.style = Paint.Style.FILL
            paint.textSize = 42f
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            canvas.drawText(
                if (effect.points > 0) "+${effect.points}" else "−5",
                effect.x,
                effect.y - 40 - progress * 65,
                paint
            )
            paint.isFakeBoldText = false
        }

        paint.alpha = 255
        game?.tiltBonus?.let { drawBonus(canvas, it) }
        canvas.restore()
    }

    private fun drawBonus(canvas: Canvas, bonus: TiltBonus) {
        canvas.save()
        canvas.translate(bonus.x, bonus.y)

        paint.style = Paint.Style.FILL
        paint.color = background
        canvas.drawCircle(0f, 0f, TiltBonus.RADIUS, paint)
        paint.style = Paint.Style.STROKE
        paint.color = foreground
        paint.strokeWidth = 5f
        canvas.drawCircle(0f, 0f, TiltBonus.RADIUS - 5, paint)

        canvas.save()
        canvas.rotate(-20f)
        canvas.drawRoundRect(-23f, -50f, 23f, 24f, 8f, 8f, paint)
        canvas.drawLine(-7f, 13f, 7f, 13f, paint)
        path.reset()
        path.moveTo(-56f, -40f)
        path.lineTo(-56f, 5f)
        path.lineTo(-66f, -5f)
        path.moveTo(-56f, 5f)
        path.lineTo(-46f, -5f)
        path.moveTo(56f, 5f)
        path.lineTo(56f, -40f)
        path.lineTo(46f, -30f)
        path.moveTo(56f, -40f)
        path.lineTo(66f, -30f)
        canvas.drawPath(path, paint)
        canvas.restore()

        paint.style = Paint.Style.FILL
        paint.textSize = 27f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        canvas.drawText(context.getString(R.string.game_tilt_bonus), 0f, 62f, paint)
        paint.isFakeBoldText = false
        canvas.restore()
    }

    private fun drawBug(
        canvas: Canvas,
        bug: Bug,
        time: Double,
        alpha: Float,
        shrink: Float
    ) {
        canvas.save()
        canvas.translate(bug.x, bug.y)
        canvas.rotate(Math.toDegrees(atan2(bug.velocityY, bug.velocityX).toDouble()).toFloat() + 90)
        canvas.scale(bug.size / 100 * shrink, bug.size / 100 * shrink)

        val gait = sin(time * 18 + bug.id).toFloat() * 6
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        path.reset()

        for (side in listOf(-1f, 1f)) {
            for (leg in 0..2) {
                val y = -12f + leg * 23
                val swing = gait * if (leg == 1) -side else side

                path.moveTo(side * 23, y)
                path.lineTo(side * 49, y + swing - 6)
                path.lineTo(side * 65, y + swing + if (leg == 0) -24 else 22)
            }

            path.moveTo(side * 13, -37f)
            path.quadTo(side * 16, -60f, side * 32, -69f)
        }

        paint.color = background
        paint.alpha = (alpha * 255).toInt()
        paint.strokeWidth = 11f
        canvas.drawPath(path, paint)

        paint.color = colors.getValue(bug.visualResource)
        paint.strokeWidth = 7f
        canvas.drawPath(path, paint)

        val body = bodies.getValue(bug.visualResource)
        body.alpha = (alpha * 255).toInt()
        body.setBounds(-50, -50, 50, 50)

        canvas.save()
        canvas.scale(1.08f, 1.06f)
        body.setTint(background)
        body.draw(canvas)
        canvas.restore()

        body.setTint(colors.getValue(bug.visualResource))
        body.draw(canvas)

        paint.alpha = 255
        paint.style = Paint.Style.FILL
        canvas.restore()
    }

    private fun tap(x: Float, y: Float) {
        if (round?.tiltBonus?.contains(x, y) == true && onBonusTap?.invoke(x, y) == true) {
            accessibility.invalidateRoot()
            performClick()
            invalidate()

            return
        }

        val bug = round?.bugs?.lastOrNull { it.contains(x, y) }
        val points = onTap?.invoke(x, y) ?: return

        effects.add(Effect(x, y, points, round?.elapsed ?: 0.0, if (points > 0) bug else null))
        accessibility.invalidateRoot()
        performClick()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!active || scale <= 0) return false

        val x = (event.x - left) / scale
        val y = (event.y - top) / scale

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureInField = x in 0f..GameRound.WIDTH && y in 0f..GameRound.HEIGHT
                return gestureInField
            }

            MotionEvent.ACTION_UP -> {
                if (gestureInField) tap(x, y)
                gestureInField = false
            }

            MotionEvent.ACTION_CANCEL -> gestureInField = false
        }

        return true
    }

    override fun performClick(): Boolean {
        super.performClick()

        return true
    }
}

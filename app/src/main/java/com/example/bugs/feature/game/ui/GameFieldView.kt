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
import com.example.bugs.domain.game.AttackResult
import com.example.bugs.domain.game.GameAbility
import com.example.bugs.domain.game.Bug
import com.example.bugs.domain.game.BugType
import com.example.bugs.domain.game.GameRound
import com.example.bugs.domain.game.TiltBonus
import com.google.android.material.color.MaterialColors
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.cos
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

    var onTap: ((Float, Float) -> AttackResult?)? = null
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
    private var multitouchGesture = false
    private val abilityIcons = GameAbility.entries.associateWith {
        AppCompatResources.getDrawable(context, it.icon)!!.mutate().apply {
            setTint(ContextCompat.getColor(context, it.color))
        }
    }

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
        game?.bugs?.forEach { bug ->
            if (game.abilityRemaining(GameAbility.SLOW) > 0) {
                drawBug(canvas, bug.copy(x = bug.x - bug.velocityX * .13f, y = bug.y - bug.velocityY * .13f), game.animationTime, .14f, 1f)
                drawBug(canvas, bug.copy(x = bug.x - bug.velocityX * .07f, y = bug.y - bug.velocityY * .07f), game.animationTime, .25f, 1f)
            }
            drawBug(canvas, bug, game.animationTime, 1f, 1f)
            if (game.abilityRemaining(GameAbility.FREEZE) > 0) {
                paint.color = ContextCompat.getColor(context, GameAbility.FREEZE.color)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                paint.alpha = 190
                path.reset()
                for (i in 0..6) {
                    val angle = i * Math.PI / 3
                    val x = bug.x + cos(angle).toFloat() * bug.size * .8f
                    val y = bug.y + sin(angle).toFloat() * bug.size * .8f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                canvas.drawPath(path, paint)
                paint.alpha = 255
                paint.style = Paint.Style.FILL
            }
        }

        effects.removeAll { (game?.elapsed ?: 0.0) - it.born >= .45 }
        effects.forEach { effect ->
            val age = ((game?.elapsed ?: effect.born) - effect.born).toFloat()
            val progress = (age / .45f).coerceIn(0f, 1f)

            effect.bug?.let { drawBug(canvas, it, effect.born, 1 - progress, 1 - progress * .4f) }

            paint.color = ContextCompat.getColor(context,
                if ((game?.abilityRemaining(GameAbility.MULTITOUCH) ?: 0.0) > 0) GameAbility.MULTITOUCH.color
                else R.color.bug_normal)
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
        if (game != null) drawAbilities(canvas, game)
        canvas.restore()
    }

    private fun drawAbilities(canvas: Canvas, game: GameRound) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND

        game.bait?.let { bait ->
            paint.color = ContextCompat.getColor(context, GameAbility.BAIT.color)
            for (i in 0..2) {
                val phase = ((game.elapsed * .65 + i / 3.0) % 1).toFloat()
                paint.alpha = ((1 - phase) * 125).toInt()
                paint.strokeWidth = 3f
                canvas.drawCircle(bait.x, bait.y, 40 + phase * 230, paint)
            }
            paint.alpha = 255
            drawAbilityIcon(canvas, GameAbility.BAIT, bait.x, bait.y, 32f)
        }

        game.abilityEffects.forEach { effect ->
            val progress = ((game.elapsed - effect.born) / .45).toFloat().coerceIn(0f, 1f)
            paint.color = ContextCompat.getColor(context, effect.ability.color)
            paint.alpha = ((1 - progress) * 230).toInt()
            paint.strokeWidth = 5f * (1 - progress) + 1
            when (effect.ability) {
                GameAbility.BOMB -> {
                    canvas.drawCircle(effect.x, effect.y, 240 * progress, paint)
                    paint.style = Paint.Style.FILL
                    paint.alpha = ((1 - progress) * 95).toInt()
                    canvas.drawCircle(effect.x, effect.y, 70 * (1 - progress), paint)
                    paint.alpha = ((1 - progress) * 230).toInt()
                    for (i in 0..11) {
                        val angle = i * Math.PI / 6
                        val distance = 240 * progress
                        canvas.drawCircle(effect.x + cos(angle).toFloat() * distance,
                            effect.y + sin(angle).toFloat() * distance, 5 * (1 - progress), paint)
                    }
                    paint.style = Paint.Style.STROKE
                }
                GameAbility.CHAIN -> {
                    effect.targets.zipWithNext().forEach { (first, second) ->
                        val dx = second.x - first.x
                        val dy = second.y - first.y
                        path.reset()
                        path.moveTo(first.x, first.y)
                        path.lineTo(first.x + dx * .33f - dy * .1f, first.y + dy * .33f + dx * .1f)
                        path.lineTo(first.x + dx * .66f + dy * .1f, first.y + dy * .66f - dx * .1f)
                        path.lineTo(second.x, second.y)
                        canvas.drawPath(path, paint)
                    }
                    effect.targets.forEach { canvas.drawCircle(it.x, it.y, 15 + progress * 65, paint) }
                }
                GameAbility.FREEZE -> canvas.drawCircle(effect.x, effect.y, progress * 900, paint)
                else -> Unit
            }
        }

        val visible = GameAbility.entries.filter {
            game.abilityRemaining(it) > 0 || game.pendingAbility == it || (it == GameAbility.BAIT && game.bait != null)
        }
        visible.forEachIndexed { index, ability ->
            val x = 60f + index * 90
            val pending = game.pendingAbility == ability
            val pulse = if (pending) 1 + sin(game.elapsed * 6).toFloat() * .1f else 1f
            paint.color = background
            paint.alpha = 235
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x, 62f, 38f * pulse, paint)
            paint.color = ContextCompat.getColor(context, ability.color)
            paint.alpha = 255
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            val remaining = if (ability == GameAbility.BAIT) {
                ((game.bait?.expiresAt ?: game.elapsed) - game.elapsed).coerceAtLeast(0.0)
            } else game.abilityRemaining(ability)
            val sweep = if (pending) 360f else (remaining / ability.duration * 360).toFloat()
            canvas.drawArc(x - 35, 27f, x + 35, 97f, -90f, sweep, false, paint)
            drawAbilityIcon(canvas, ability, x, 62f, 24f * pulse)
        }
        paint.alpha = 255
        paint.style = Paint.Style.FILL
    }

    private fun drawAbilityIcon(canvas: Canvas, ability: GameAbility, x: Float, y: Float, radius: Float) {
        abilityIcons.getValue(ability).apply {
            setBounds((x - radius).toInt(), (y - radius).toInt(), (x + radius).toInt(), (y + radius).toInt())
            draw(canvas)
        }
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
        if (round?.pendingAbility == null && round?.tiltBonus?.contains(x, y) == true && onBonusTap?.invoke(x, y) == true) {
            accessibility.invalidateRoot()
            performClick()
            invalidate()

            return
        }

        val result = onTap?.invoke(x, y) ?: return
        if (result.bugs.isEmpty() && result.points < 0) {
            effects.add(Effect(x, y, result.points, round?.elapsed ?: 0.0, null))
        }
        result.bugs.forEach { bug ->
            effects.add(Effect(bug.x, bug.y, bug.points, round?.elapsed ?: 0.0, bug))
        }
        accessibility.invalidateRoot()
        performClick()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!active || scale <= 0) return false

        val index = event.actionIndex
        val x = (event.getX(index) - left) / scale
        val y = (event.getY(index) - top) / scale

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureInField = x in 0f..GameRound.WIDTH && y in 0f..GameRound.HEIGHT
                multitouchGesture = (round?.abilityRemaining(GameAbility.MULTITOUCH) ?: 0.0) > 0
                if (gestureInField) {
                    parent.requestDisallowInterceptTouchEvent(multitouchGesture)
                    if (multitouchGesture) tap(x, y)
                }
                return gestureInField
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (gestureInField && (round?.abilityRemaining(GameAbility.MULTITOUCH) ?: 0.0) > 0) {
                    multitouchGesture = true
                    tap(x, y)
                }
            }

            MotionEvent.ACTION_UP -> {
                if (gestureInField && !multitouchGesture) tap(x, y)
                gestureInField = false
                multitouchGesture = false
                parent.requestDisallowInterceptTouchEvent(false)
            }

            MotionEvent.ACTION_CANCEL -> {
                gestureInField = false
                multitouchGesture = false
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }

        return true
    }

    override fun performClick(): Boolean {
        super.performClick()

        return true
    }
}

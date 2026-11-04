package com.example.bugs.feature.game.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.Surface
import com.example.bugs.R
import kotlin.math.sqrt

class GameTiltController(
    private val context: Context,
    private val onTilt: (Float, Float) -> Unit,
    private val onUnavailable: () -> Unit
) : SensorEventListener {

    companion object {
        private const val DEAD_ZONE = .087f
        private const val SOUND_STOP = .07f
        private const val FULL_TILT = .5f
    }

    private val manager = context.getSystemService(SensorManager::class.java)
    private var sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gravity = FloatArray(3)
    private var sampled = false
    private var running = false
    private var magnitude = 0f
    private var soundLoaded = false
    private var stream = 0
    private val sounds = runCatching {
        SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }.getOrNull()
    private val scream: Int

    val available get() = sensor != null

    init {
        sounds?.setOnLoadCompleteListener { _, _, status -> soundLoaded = status == 0 }
        scream = runCatching { sounds?.load(context, R.raw.bug_scream, 1) ?: 0 }.getOrDefault(0)
    }

    fun setRunning(active: Boolean) {
        if (running == active || active && !available) return

        if (active) {
            var registered = sensor?.let {
                manager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) == true
            } == true

            if (!registered && sensor?.type == Sensor.TYPE_GRAVITY) {
                sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                registered = sensor?.let {
                    manager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) == true
                } == true
            }

            running = registered
            if (!registered) {
                sensor = null
                onUnavailable()
            }
        } else {
            running = false
            manager?.unregisterListener(this)
            sampled = false
            magnitude = 0f
            onTilt(0f, 0f)
            stopSound()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!running || event.values.size < 3 || (0..2).any { !event.values[it].isFinite() }) return

        val smooth = event.sensor.type == Sensor.TYPE_ACCELEROMETER && sampled
        for (index in gravity.indices) {
            gravity[index] = if (smooth) gravity[index] * .85f + event.values[index] * .15f else event.values[index]
        }
        sampled = true

        val x: Float
        val y: Float

        when (context.display?.rotation) {
            Surface.ROTATION_90 -> {
                x = -gravity[1]
                y = -gravity[0]
            }
            Surface.ROTATION_180 -> {
                x = gravity[0]
                y = -gravity[1]
            }
            Surface.ROTATION_270 -> {
                x = gravity[1]
                y = gravity[0]
            }
            else -> {
                x = -gravity[0]
                y = gravity[1]
            }
        }

        val length = sqrt(x * x + y * y)
        magnitude = length / SensorManager.GRAVITY_EARTH
        val strength = ((magnitude - DEAD_ZONE) / (FULL_TILT - DEAD_ZONE)).coerceIn(0f, 1f)

        if (length > 0) onTilt(x / length * strength, y / length * strength) else onTilt(0f, 0f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun updateSound(effectActive: Boolean) {
        val threshold = if (stream == 0) DEAD_ZONE else SOUND_STOP

        if (running && effectActive && soundLoaded && magnitude > threshold) {
            if (stream == 0) stream = sounds?.play(scream, .45f, .45f, 1, -1, 1f) ?: 0
        } else {
            stopSound()
        }
    }

    private fun stopSound() {
        if (stream != 0) sounds?.stop(stream)
        stream = 0
    }

    fun release() {
        setRunning(false)
        sounds?.release()
    }
}

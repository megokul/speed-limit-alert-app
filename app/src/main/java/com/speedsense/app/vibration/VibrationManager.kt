package com.speedsense.app.vibration

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class VibrationManager(
    context: Context,
) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(VibratorManager::class.java)
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun vibrateForSpeedLimit(speedLimit: Int) {
        val deviceVibrator = vibrator ?: return
        if (!deviceVibrator.hasVibrator()) {
            return
        }

        val effect = when (speedLimit) {
            20 -> waveform(longArrayOf(0, 120), intArrayOf(0, 255))
            30 -> waveform(longArrayOf(0, 90, 90, 90), intArrayOf(0, 255, 0, 255))
            40 -> waveform(longArrayOf(0, 320), intArrayOf(0, 255))
            50 -> waveform(longArrayOf(0, 280, 100, 100), intArrayOf(0, 255, 0, 220))
            60 -> waveform(longArrayOf(0, 90, 80, 90, 80, 90), intArrayOf(0, 255, 0, 255, 0, 255))
            70 -> waveform(longArrayOf(0, 260, 120, 260), intArrayOf(0, 255, 0, 255))
            else -> VibrationEffect.createOneShot(140, VibrationEffect.DEFAULT_AMPLITUDE)
        }

        deviceVibrator.vibrate(effect)
    }

    private fun waveform(timings: LongArray, amplitudes: IntArray): VibrationEffect {
        return VibrationEffect.createWaveform(timings, amplitudes, -1)
    }
}

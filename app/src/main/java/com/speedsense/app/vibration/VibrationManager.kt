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

        // 20=2 short, 30=3 short, 40=4 short
        // 50=1 long, 60=2 long, 70=3 long
        val effect = when (speedLimit) {
            20 -> waveform(longArrayOf(0, 200, 200, 200), intArrayOf(0, 255, 0, 255))
            30 -> waveform(longArrayOf(0, 200, 200, 200, 200, 200), intArrayOf(0, 255, 0, 255, 0, 255))
            40 -> waveform(longArrayOf(0, 200, 200, 200, 200, 200, 200, 200), intArrayOf(0, 255, 0, 255, 0, 255, 0, 255))
            50 -> waveform(longArrayOf(0, 500), intArrayOf(0, 255))
            60 -> waveform(longArrayOf(0, 500, 300, 500), intArrayOf(0, 255, 0, 255))
            70 -> waveform(longArrayOf(0, 500, 300, 500, 300, 500), intArrayOf(0, 255, 0, 255, 0, 255))
            else -> VibrationEffect.createOneShot(200, 255)
        }

        deviceVibrator.vibrate(effect)
    }

    fun cancel() {
        vibrator?.cancel()
    }

    private fun waveform(timings: LongArray, amplitudes: IntArray): VibrationEffect {
        return VibrationEffect.createWaveform(timings, amplitudes, -1)
    }
}

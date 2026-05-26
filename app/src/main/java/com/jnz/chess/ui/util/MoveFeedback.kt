package com.jnz.chess.ui.util

import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

fun createSoundPool(): SoundPool {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_GAME)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    } else {
        @Suppress("DEPRECATION")
        SoundPool(4, android.media.AudioManager.STREAM_MUSIC, 0)
    }
}

fun playMoveSound(soundPool: SoundPool) {
    try {
        val toneGen = android.media.ToneGenerator(
            android.media.AudioManager.STREAM_MUSIC, 50
        )
        toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 80)
    } catch (_: Exception) {
        // ToneGenerator may fail on some devices — skip silently
    }
}

fun vibrateMove(vibrator: Vibrator) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    } catch (_: SecurityException) {
        // Permission may be denied — skip vibration
    }
}

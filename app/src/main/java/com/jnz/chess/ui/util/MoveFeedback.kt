package com.jnz.chess.ui.util

import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun createSoundPool(): SoundPool {
    return SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_GAME)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
}

private var toneScope: CoroutineScope? = null

fun initToneScope(scope: CoroutineScope) {
    toneScope = scope
}

fun playMoveSound() {
    try {
        val toneGen = android.media.ToneGenerator(
            android.media.AudioManager.STREAM_MUSIC, 50
        )
        toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 80)
        toneScope?.launch(Dispatchers.IO) {
            delay(100)
            toneGen.release()
        } ?: toneGen.release()
    } catch (_: Exception) {
    }
}

fun vibrateMove(vibrator: Vibrator) {
    try {
        vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (_: SecurityException) {
    }
}

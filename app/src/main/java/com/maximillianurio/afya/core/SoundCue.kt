package com.maximillianurio.afya.core

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager

/** Confirmation cue when a weigh-in locks. Synthesised, so no third-party audio is shipped. */
object SoundCue {
    fun stable(context: Context, sound: Boolean, haptics: Boolean) {
        if (haptics) buzz(context)
        if (!sound) return
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 180)
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 400)
        }
    }

    private fun buzz(context: Context) {
        runCatching {
            val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
            vibrator.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}

package com.autombot.security.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.util.Log

/**
 * Toca um som de alarme no volume máximo — usado tanto para assustar/alertar
 * quem está tentando desbloquear o aparelho quanto para a função de
 * "encontrar o aparelho perdido dentro de casa".
 */
class AlarmHelper(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    fun playAlarm(durationMs: Long = 15_000L) {
        stopAlarm()

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, alarmUri)
                isLooping = true
                prepare()
                start()
            }

            // Para automaticamente após a duração configurada
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                stopAlarm()
            }, durationMs)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao tocar alarme", e)
        }
    }

    fun stopAlarm() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
    }

    companion object {
        private const val TAG = "AlarmHelper"
    }
}

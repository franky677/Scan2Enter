package com.scan2enter.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.scan2enter.R

object ScanFeedbackManager {

    private const val TAG = "Scan2EnterAudio"
    private const val PREFS_NAME = "scan_feedback"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_VOLUME = "volume"
    private const val DEFAULT_VOLUME = 0.85f
    private const val STOCK_VOICE_DELAY_MS = 550L

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingStockVoice: Runnable? = null

    @Volatile private var initialized = false
    @Volatile private var enabled = true
    @Volatile private var volume = DEFAULT_VOLUME

    private var soundPool: SoundPool? = null
    private var successSoundId = 0
    private var warningSoundId = 0
    private var errorSoundId = 0
    private var blockedSoundId = 0
    private var understockSoundId = 0
    private var reorderSoundId = 0
    private val loadedSoundIds = mutableSetOf<Int>()

    fun initialize(context: Context) {
        if (initialized) return

        synchronized(lock) {
            if (initialized) return

            val appContext = context.applicationContext
            val preferences = appContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

            enabled = preferences.getBoolean(KEY_ENABLED, true)
            volume = preferences.getFloat(
                KEY_VOLUME,
                DEFAULT_VOLUME
            ).coerceIn(0f, 1f)

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(attributes)
                .build()

            pool.setOnLoadCompleteListener { _, sampleId, status ->
                synchronized(lock) {
                    if (status == 0) {
                        loadedSoundIds.add(sampleId)
                        Log.d(TAG, "SUONO CARICATO id=$sampleId")
                    } else {
                        Log.e(TAG, "ERRORE CARICAMENTO id=$sampleId status=$status")
                    }
                }
            }

            successSoundId = pool.load(appContext, R.raw.scan_blip, 1)
            warningSoundId = pool.load(appContext, R.raw.scan_crash, 1)
            errorSoundId = pool.load(appContext, R.raw.scan_double_beep, 1)
            blockedSoundId = pool.load(appContext, R.raw.article_blocked, 1)
            understockSoundId = pool.load(appContext, R.raw.article_understock, 1)
            reorderSoundId = pool.load(appContext, R.raw.article_reorder, 1)

            soundPool = pool
            initialized = true

            Log.d(TAG, "AUDIO INIZIALIZZATO volume=$volume")
        }
    }

    fun playSuccess(context: Context) {
        initialize(context)
        play(successSoundId, "BLIP")
    }

    fun playWarning(context: Context) {
        initialize(context)
        play(warningSoundId, "CRASH")
    }

    fun playError(context: Context) {
        initialize(context)
        play(errorSoundId, "DOPPIO BIP")
    }

    fun playBlocked(context: Context) {
        initialize(context)
        cancelPendingStockVoice()
        play(blockedSoundId, "ARTICOLO BLOCCATO")
    }

    fun playUnderstock(context: Context) {
        initialize(context)
        playStockSequence(
            voiceSoundId = understockSoundId,
            voiceLabel = "ARTICOLO SOTTO SCORTA"
        )
    }

    fun playReorder(context: Context) {
        initialize(context)
        playStockSequence(
            voiceSoundId = reorderSoundId,
            voiceLabel = "ARTICOLO DA RIORDINARE"
        )
    }

    fun setEnabled(context: Context, value: Boolean) {
        initialize(context)
        enabled = value
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, value)
            .apply()
    }

    fun setVolume(context: Context, value: Float) {
        initialize(context)
        volume = value.coerceIn(0f, 1f)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_VOLUME, volume)
            .apply()
    }

    fun getVolume(context: Context): Float {
        initialize(context)
        return volume
    }

    fun isEnabled(context: Context): Boolean {
        initialize(context)
        return enabled
    }

    fun release() {
        synchronized(lock) {
            cancelPendingStockVoice()
            soundPool?.release()
            soundPool = null
            loadedSoundIds.clear()
            successSoundId = 0
            warningSoundId = 0
            errorSoundId = 0
            blockedSoundId = 0
            understockSoundId = 0
            reorderSoundId = 0
            initialized = false
        }
    }

    private fun playStockSequence(
        voiceSoundId: Int,
        voiceLabel: String
    ) {
        cancelPendingStockVoice()
        play(warningSoundId, "CRASH")

        val voiceRunnable = Runnable {
            pendingStockVoice = null
            play(voiceSoundId, voiceLabel)
        }

        pendingStockVoice = voiceRunnable
        mainHandler.postDelayed(
            voiceRunnable,
            STOCK_VOICE_DELAY_MS
        )
    }

    private fun cancelPendingStockVoice() {
        pendingStockVoice?.let(mainHandler::removeCallbacks)
        pendingStockVoice = null
    }

    private fun play(soundId: Int, label: String) {
        if (!enabled || soundId == 0) return

        val pool = soundPool ?: return

        synchronized(lock) {
            if (!loadedSoundIds.contains(soundId)) {
                Log.d(TAG, "SUONO NON PRONTO label=$label")
                return
            }
        }

        pool.play(
            soundId,
            1.0f,
            1.0f,
            1,
            0,
            1f
        )
    }
}
package com.opic.commute.logic

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/** 안드로이드 기본 TTS (영어 원어민 음성) */
class Tts(context: Context) : TextToSpeech.OnInitListener {
    private val main = Handler(Looper.getMainLooper())
    private val callbacks = HashMap<String, () -> Unit>()
    private val tts = TextToSpeech(context.applicationContext, this)

    var ready by mutableStateOf(false)
        private set
    var missingVoice by mutableStateOf(false)
        private set
    var speakingId by mutableStateOf<String?>(null)
        private set

    var rate: Float = 0.9f

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            missingVoice = true
            return
        }
        val r = tts.setLanguage(Locale.US)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            missingVoice = true
            tts.setLanguage(Locale.ENGLISH)
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                main.post { speakingId = utteranceId }
            }

            override fun onDone(utteranceId: String?) {
                main.post { finish(utteranceId) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { finish(utteranceId) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                main.post { finish(utteranceId) }
            }
        })
        ready = true
    }

    private fun finish(id: String?) {
        if (id == null) return
        if (speakingId == id) speakingId = null
        callbacks.remove(id)?.invoke()
    }

    fun speak(text: String, id: String = UUID.randomUUID().toString(), slow: Boolean = false, onDone: (() -> Unit)? = null) {
        callbacks.clear()
        if (onDone != null) callbacks[id] = onDone
        tts.setSpeechRate(if (slow) rate * 0.7f else rate)
        speakingId = id
        val res = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (res == TextToSpeech.ERROR) finish(id)
    }

    suspend fun speakAwait(text: String, id: String = UUID.randomUUID().toString()) {
        suspendCancellableCoroutine { cont ->
            speak(text, id) { if (cont.isActive) cont.resume(Unit) }
            cont.invokeOnCancellation { main.post { if (speakingId == id) stop() } }
        }
    }

    fun stop() {
        callbacks.clear()
        speakingId = null
        tts.stop()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }
}

/** 안드로이드 기본 음성 인식 (영어). continuous=true 이면 멈춤을 누를 때까지 이어서 받아 적습니다. */
class Stt(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var continuous = false
    private var onFinish: ((String) -> Unit)? = null
    private var retries = 0

    var listening by mutableStateOf(false)
        private set
    var committed by mutableStateOf("")
        private set
    var partial by mutableStateOf("")
        private set
    var level by mutableStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val liveText: String get() = (committed + " " + partial).trim()

    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(continuous: Boolean, onFinish: (String) -> Unit) {
        cancel()
        this.continuous = continuous
        this.onFinish = onFinish
        committed = ""
        partial = ""
        error = null
        retries = 0
        if (!available()) {
            error = "이 기기에서 음성 인식 서비스를 찾지 못했어요. 설정 > 일반 > 음성 입력에서 Google 음성 인식을 켜 주세요."
            listening = true
            finish()
            return
        }
        listening = true
        begin()
    }

    suspend fun listenAwait(): String = suspendCancellableCoroutine { cont ->
        start(continuous = false) { if (cont.isActive) cont.resume(it) }
        cont.invokeOnCancellation { main.post { cancel() } }
    }

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, if (continuous) 4000L else 2000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, if (continuous) 3000L else 1500L)
    }

    private fun begin() {
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        r.setRecognitionListener(listener)
        recognizer = r
        r.startListening(intent())
    }

    private fun append(t: String) {
        if (t.isNotBlank()) committed = (committed + " " + t).trim()
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            level = rmsdB
        }

        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(code: Int) {
            if (!listening) return
            val silent = code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            val transient = code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || code == SpeechRecognizer.ERROR_CLIENT
            if (continuous && (silent || transient) && retries < 8) {
                retries++
                if (partial.isNotBlank()) append(partial)
                partial = ""
                main.postDelayed({ if (listening) begin() }, 250)
                return
            }
            if (partial.isNotBlank()) append(partial)
            if (committed.isBlank()) {
                error = when (code) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 필요해요. 앱 설정에서 마이크를 허용해 주세요."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 문제로 인식하지 못했어요. 다시 시도해 주세요."
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "말소리를 알아듣지 못했어요. 조금 더 크게 말해 보세요."
                    else -> "음성 인식 오류($code). 다시 시도해 주세요."
                }
            }
            finish()
        }

        override fun onResults(results: Bundle?) {
            if (!listening) return
            val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            append(t.ifBlank { partial })
            partial = ""
            retries = 0
            if (continuous) main.postDelayed({ if (listening) begin() }, 100) else finish()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!t.isNullOrBlank()) partial = t
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /** 사용자가 멈춤을 눌렀을 때: 지금까지 들은 내용으로 마칩니다. */
    fun stop() {
        if (!listening) return
        if (partial.isNotBlank()) append(partial)
        partial = ""
        finish()
    }

    private fun finish() {
        listening = false
        level = 0f
        recognizer?.let {
            it.cancel()
            it.destroy()
        }
        recognizer = null
        val cb = onFinish
        onFinish = null
        cb?.invoke(committed.trim())
    }

    /** 결과 없이 취소 */
    fun cancel() {
        onFinish = null
        if (listening || recognizer != null) {
            listening = false
            recognizer?.let {
                it.cancel()
                it.destroy()
            }
            recognizer = null
        }
        partial = ""
        level = 0f
    }
}

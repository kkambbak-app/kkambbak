package app.kkambbak.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import java.util.Locale
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.tts.constant.Mode as TtsMode

/**
 * 음성 입출력. Pleos TextToSpeech / SpeechToText SDK(온디바이스, 한국어)를 먼저 쓰고,
 * Pleos 음성 서비스가 없는 기기(일반 에뮬레이터 등)에서는 안드로이드 기본 TTS·음성인식으로 대신한다.
 */
class VoiceIO(
    private val context: Context,
    private val onSpeaking: (Boolean) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onStatus: (tts: String, stt: String, ttsOk: Boolean, sttOk: Boolean) -> Unit,
) {
    private val tag = "kkambbak.voice"
    private val main = Handler(Looper.getMainLooper())

    private var pleosTts: TextToSpeech? = null
    private var pleosTtsReady = false
    private var androidTts: android.speech.tts.TextToSpeech? = null
    private var androidTtsReady = false
    private var pleosStt: SpeechToText? = null
    private var pleosSttReady = false
    private var androidStt: SpeechRecognizer? = null
    /** 리워드 '브리핑 목소리 · 두 번째'. Pleos 차량에서는 차량이 제공하는 목소리 목록이 확인되면 setSpeakerType으로 연결한다. */
    var altVoice = false
        set(v) { field = v; androidTts?.setPitch(if (v) 0.85f else 1.0f) }

    private var ttsLabel = "확인 중"; private var sttLabel = "확인 중"
    private var ttsOk = false; private var sttOk = false
    private fun report() = main.post { onStatus(ttsLabel, sttLabel, ttsOk, sttOk) }

    fun init() {
        initTts()
        initStt()
    }

    private fun initTts() {
        try {
            val t = TextToSpeech(context.applicationContext, TtsMode.ON_DEVICE)
            pleosTts = t
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    pleosTtsReady = true
                    t.setLocale(Locale.KOREA)
                    t.addEventListener(ttsEvents)
                    ttsLabel = "Pleos TTS · 한국어 · 온디바이스"; ttsOk = true; report()
                }
                override fun onFailure(errorType: ErrorType) { Log.w(tag, "Pleos TTS 실패 $errorType"); fallbackTts() }
            })
        } catch (t: Throwable) {
            Log.w(tag, "Pleos TTS 사용 불가", t); fallbackTts()
        }
    }

    private val ttsEvents = object : EventListener {
        override fun onStart() { main.post { onSpeaking(true) } }
        override fun onReady() {}
        override fun onDone() { main.post { onSpeaking(false) } }
        override fun onError(errorType: ErrorType) { main.post { onSpeaking(false) } }
        override fun onStop() { main.post { onSpeaking(false) } }
        override fun onUpdatedRms(rms: Double) {}
    }

    private fun fallbackTts() {
        main.post {
            if (androidTts != null) return@post
            androidTts = android.speech.tts.TextToSpeech(context.applicationContext) { status ->
                androidTtsReady = status == android.speech.tts.TextToSpeech.SUCCESS
                if (androidTtsReady) {
                    androidTts?.language = Locale.KOREA
                    androidTts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(id: String?) { main.post { onSpeaking(true) } }
                        override fun onDone(id: String?) { main.post { onSpeaking(false) } }
                        @Deprecated("Deprecated in Java") override fun onError(id: String?) { main.post { onSpeaking(false) } }
                    })
                }
                ttsLabel = if (androidTtsReady) "Pleos TTS 없음 · 기본 TTS로 대체" else "음성 출력 불가"
                ttsOk = androidTtsReady; report()
            }
        }
    }

    fun speak(text: String) {
        when {
            pleosTtsReady -> runCatching { pleosTts?.stop(); pleosTts?.speak(text, true) }.onFailure { Log.w(tag, "speak 실패", it) }
            androidTtsReady -> androidTts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "kkambbak")
            else -> Log.w(tag, "TTS 없음: $text")
        }
    }

    fun stopSpeaking() {
        runCatching { pleosTts?.stop() }
        runCatching { androidTts?.stop() }
    }

    /** 초보 모드 알림음: 짧고 낮은 비프 한 번. */
    fun chime() {
        runCatching {
            val g = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
            g.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
            main.postDelayed({ g.release() }, 600)
        }
    }

    // ------------------------------------------------------------ STT

    private fun initStt() {
        try {
            val s = SpeechToText(context.applicationContext, SttMode.ON_DEVICE)
            pleosStt = s
            s.initialize(object : ai.pleos.playground.stt.listener.OnInitializeListener {
                override fun onSuccess() {
                    pleosSttReady = true
                    s.setLocale(Locale.KOREA)
                    s.addListener(sttListener)
                    sttLabel = "Pleos STT · 한국어 · 온디바이스"; sttOk = true; report()
                }
                override fun onFailure(errorType: ai.pleos.playground.stt.constant.ErrorType) { Log.w(tag, "Pleos STT 실패 $errorType"); fallbackStt() }
            })
        } catch (t: Throwable) {
            Log.w(tag, "Pleos STT 사용 불가", t); fallbackStt()
        }
    }

    private val sttListener = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            main.post { if (isFinal) { onListening(false); onFinal(text) } else onPartial(text) }
        }
        override fun onUpdatedEpdData(p0: Long, p1: Long) {}
        override fun onStartedRecognition() { main.post { onListening(true) } }
        override fun onEndedRecognition() { main.post { onListening(false) } }
        override fun onError(errorType: ai.pleos.playground.stt.constant.ErrorType) { main.post { onListening(false) } }
        override fun onReady() {}
        override fun onUpdatedRms(rms: Double) {}
    }

    private fun fallbackStt() {
        main.post {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                sttLabel = "Pleos STT 없음 · 기본 음성인식으로 대체"; sttOk = true
            } else {
                sttLabel = "음성 인식 불가(시연 입력 사용)"; sttOk = false
            }
            report()
        }
    }

    val canListen: Boolean get() = pleosSttReady || sttOk

    fun listen() {
        if (pleosSttReady) {
            runCatching { pleosStt?.request() }.onFailure { onListening(false) }
            onListening(true)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) { onListening(false); return }
        val r = androidStt ?: SpeechRecognizer.createSpeechRecognizer(context).also { androidStt = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onListening(true) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { onListening(false) }
            override fun onResults(results: Bundle?) {
                onListening(false)
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onFinal)
            }
            override fun onPartialResults(partial: Bundle?) {
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        })
    }

    fun stopListening() {
        runCatching { pleosStt?.stop() }
        runCatching { androidStt?.stopListening() }
        onListening(false)
    }

    fun release() {
        runCatching { pleosTts?.removeEventListener(ttsEvents); pleosTts?.release() }
        runCatching { androidTts?.shutdown() }
        runCatching { pleosStt?.removeListener(sttListener); pleosStt?.release() }
        runCatching { androidStt?.destroy() }
    }
}

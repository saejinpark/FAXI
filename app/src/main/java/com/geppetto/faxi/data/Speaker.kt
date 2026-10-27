package com.geppetto.faxi.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import java.util.Locale
import ai.pleos.playground.tts.TextToSpeech as PleosTts

/**
 * 음성 안내 창구.
 * - PleosSpeaker: Pleos TextToSpeech SDK로 읽는다. 차량용.
 * - AndroidSpeaker: 폰에 들어 있는 TTS(삼성·구글 음성)로 읽는다. 폴드 시연용, 그리고 Pleos TTS가 안 될 때.
 */
interface Speaker {
    fun speak(text: String)
    fun shutdown()

    companion object {
        /** 차면 Pleos TTS, 아니면 기기 TTS */
        fun create(context: Context): Speaker =
            if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) PleosSpeaker(context) else AndroidSpeaker(context)
    }
}

/**
 * Pleos TextToSpeech SDK(`ai.pleos.playground:TextToSpeech`, 권한 pleos.car.permission.TTS_SERVICE).
 * - ON_DEVICE 모드: 차 안의 음성 엔진만 쓴다. 네트워크·앱 등록(My Project) 없이 되고, 장부 내용(이름·금액)이 차 밖으로 나가지 않는다.
 * - 새 말을 하면 하던 말은 멈춘다(독촉 중에 탑승 인사가 겹치지 않게).
 * - 초기화에 실패하면 기기 TTS(AndroidSpeaker)로 넘어간다.
 * 콜백 스레드는 문서에 없어서 메인으로 옮긴다.
 */
class PleosSpeaker(context: Context) : Speaker {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val tts = PleosTts(app, Mode.ON_DEVICE)
    private var ready = false
    private var fallback: Speaker? = null
    private var pending: String? = null
    /** 엔진이 이 차에서 못 읽으면 기기 TTS로 다시 읽을 마지막 문장 */
    private var lastText: String? = null

    private val events = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onDone() = Unit
        override fun onStop() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onError(error: ErrorType) {
            Log.w(TAG, "Pleos TTS 오류 $error")
            // 엔진·언어를 이 차가 못 쓰는 경우는 다음에도 안 되니 기기 TTS로 바꾸고 방금 문장을 다시 읽는다
            if (error in Unsupported) main.post {
                val text = lastText
                useFallback("읽기 오류 $error")
                text?.let { fallback?.speak(it) }
            }
        }
    }

    init {
        runCatching {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        runCatching {
                            tts.addEventListener(events)
                            // 나라까지 붙여야 한다: Locale.KOREAN은 엔진에 "ko-"로 가서 unsupported locale로 실패한다(에뮬레이터 로그로 확인)
                            tts.setLocale(Locale.KOREA)
                        }
                        ready = true
                        pending?.let(::speak)
                        pending = null
                    }
                }
                override fun onFailure(error: ErrorType) {
                    main.post { useFallback("초기화 실패 $error") }
                }
            })
        }.onFailure { useFallback("초기화 예외 ${it.message}") }
    }

    override fun speak(text: String) {
        fallback?.let { return it.speak(text) }
        if (!ready) {
            pending = text
            return
        }
        lastText = text
        runCatching {
            tts.stop()
            tts.speak(text, true)
        }.onFailure { useFallback("읽기 실패 ${it.message}"); fallback?.speak(text) }
    }

    override fun shutdown() {
        fallback?.shutdown()
        runCatching {
            tts.stop()
            tts.removeEventListener(events)
            tts.release()
        }
    }

    private fun useFallback(why: String) {
        if (fallback != null) return
        Log.w(TAG, "Pleos TTS 대신 기기 TTS를 써요: $why")
        fallback = AndroidSpeaker(app).also { f -> pending?.let(f::speak) }
        pending = null
    }

    private companion object {
        const val TAG = "FaxiSpeaker"
        val Unsupported = setOf(ErrorType.ON_DEVICE_ENGINE_ERROR, ErrorType.UNAVAILABLE_LANGUAGE, ErrorType.NOT_SUPPORTED_API)
    }
}

class AndroidSpeaker(context: Context) : Speaker, TextToSpeech.OnInitListener {
    private var ready = false
    private var pending: String? = null
    private val tts = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val result = tts.setLanguage(Locale.KOREAN)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) return
        ready = true
        pending?.let { speak(it) }
        pending = null
    }

    override fun speak(text: String) {
        if (!ready) {
            pending = text   // 엔진 준비 전이면 준비되는 즉시 읽는다
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "faxi")
    }

    override fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}

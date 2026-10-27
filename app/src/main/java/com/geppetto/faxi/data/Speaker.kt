package com.geppetto.faxi.data

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 음성 안내 창구.
 * - AndroidSpeaker: 폰에 들어 있는 TTS(삼성·구글 음성)로 읽는다. 폴드 시연용.
 * - PleosSpeaker: Pleos TextToSpeech SDK로 읽는다. 차량용 (다음 단계)
 */
interface Speaker {
    fun speak(text: String)
    fun shutdown()
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

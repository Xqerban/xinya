package com.xinya.dtx.core.speech

import android.content.Context
import com.iflytek.cloud.ErrorCode
import com.iflytek.cloud.InitListener
import com.iflytek.cloud.RecognizerListener
import com.iflytek.cloud.RecognizerResult
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechError
import com.iflytek.cloud.SpeechEvent
import com.iflytek.cloud.SpeechRecognizer
import com.iflytek.cloud.util.ResourceUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

@Singleton
class MscOfflineIatEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val initializer: IflytekMscInitializer,
) : OfflineIatEngine {
    private val _results = MutableSharedFlow<IatRecognitionResult>(extraBufferCapacity = 16)
    override val results: Flow<IatRecognitionResult> = _results
    private val _volumes = MutableSharedFlow<Int>(extraBufferCapacity = 32)
    override val volumes: Flow<Int> = _volumes

    private var recognizer: SpeechRecognizer? = null

    override suspend fun startListening() {
        initializer.ensureInitialized().getOrThrow()
        val speechRecognizer = recognizer ?: createRecognizer().also { recognizer = it }
        configureRecognizer(speechRecognizer)
        val code = speechRecognizer.startListening(recognizerListener)
        if (code != ErrorCode.SUCCESS) {
            throw IllegalStateException("MSC offline IAT startListening failed with code=$code")
        }
    }

    override suspend fun stopListening() {
        recognizer?.stopListening()
    }

    private fun createRecognizer(): SpeechRecognizer {
        var initCode = ErrorCode.SUCCESS
        val recognizer = SpeechRecognizer.createRecognizer(
            context,
            InitListener { code -> initCode = code }
        )
        if (recognizer == null || initCode != ErrorCode.SUCCESS) {
            throw IllegalStateException("MSC offline IAT init failed with code=$initCode")
        }
        return recognizer
    }

    private fun configureRecognizer(recognizer: SpeechRecognizer) {
        recognizer.setParameter(SpeechConstant.PARAMS, null)
        recognizer.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL)
        recognizer.setParameter(SpeechConstant.RESULT_TYPE, "json")
        recognizer.setParameter(ResourceUtil.ASR_RES_PATH, buildResourcePath())
        recognizer.setParameter(SpeechConstant.LANGUAGE, "zh_cn")
        recognizer.setParameter(SpeechConstant.ACCENT, "mandarin")
        recognizer.setParameter(SpeechConstant.VAD_BOS, "4000")
        recognizer.setParameter(SpeechConstant.VAD_EOS, "1000")
        recognizer.setParameter(SpeechConstant.ASR_PTT, "1")
        recognizer.setParameter(SpeechConstant.AUDIO_FORMAT, "wav")
        recognizer.setParameter(
            SpeechConstant.ASR_AUDIO_PATH,
            context.getExternalFilesDir("msc")?.absolutePath + "/iat.wav"
        )
    }

    private fun buildResourcePath(): String {
        val common = ResourceUtil.generateResourcePath(
            context,
            ResourceUtil.RESOURCE_TYPE.assets,
            "iflytek/iat/common.jet"
        )
        val iat = ResourceUtil.generateResourcePath(
            context,
            ResourceUtil.RESOURCE_TYPE.assets,
            "iflytek/iat/sms_16k.jet"
        )
        return "$common;$iat"
    }

    private val recognizerListener = object : RecognizerListener {
        override fun onBeginOfSpeech() = Unit

        override fun onError(error: SpeechError) {
            _results.tryEmit(
                IatRecognitionResult(
                    text = "MSC错误: ${error.getPlainDescription(true)}",
                    isFinal = true,
                    rawPayload = null,
                )
            )
        }

        override fun onEndOfSpeech() = Unit

        override fun onResult(results: RecognizerResult, isLast: Boolean) {
            val payload = results.resultString.orEmpty()
            _results.tryEmit(
                IatRecognitionResult(
                    text = MscResultParser.parseIatResult(payload),
                    isFinal = isLast,
                    rawPayload = payload,
                )
            )
        }

        override fun onVolumeChanged(volume: Int, data: ByteArray?) {
            _volumes.tryEmit(volume)
        }

        override fun onEvent(eventType: Int, arg1: Int, arg2: Int, obj: android.os.Bundle?) {
            if (eventType == SpeechEvent.EVENT_SESSION_ID) {
                obj?.getString(SpeechEvent.KEY_EVENT_SESSION_ID)
            }
        }
    }
}

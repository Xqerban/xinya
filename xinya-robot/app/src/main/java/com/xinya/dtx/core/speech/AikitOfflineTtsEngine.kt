package com.xinya.dtx.core.speech

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.iflytek.aikit.core.AeeEvent
import com.iflytek.aikit.core.AiHandle
import com.iflytek.aikit.core.AiHelper
import com.iflytek.aikit.core.AiListener
import com.iflytek.aikit.core.AiRequest
import com.iflytek.aikit.core.AiResponse
import com.iflytek.aikit.core.AiText
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Singleton
class AikitOfflineTtsEngine @Inject constructor(
    private val initializer: AikitSdkInitializer,
    private val config: IflytekOfflineConfig,
) : OfflineTtsEngine {
    private val mutex = Mutex()
    private val currentHandle = AtomicReference<AiHandle?>()
    private val completion = AtomicReference<CompletableDeferred<Unit>?>()
    private var audioTrack: AudioTrack? = null
    private var listenerRegistered = false

    override suspend fun speak(text: String, interruptCurrent: Boolean, volume: Int) {
        require(text.isNotBlank()) { "TTS text must not be blank." }
        require(volume in 0..100) { "TTS volume must be between 0 and 100." }
        mutex.withLock {
            initializer.ensureInitialized()
            registerListenerIfNeeded()
            if (interruptCurrent) {
                stopInternal()
            }
            ensureAudioTrack()

            val handle = AiHelper.getInst().start(
                config.aiKitTtsAbilityId,
                AiRequest.builder()
                    .param("vcn", "xiaoyan")
                    .param("language", 1)
                    .param("textEncoding", "UTF-8")
                    .param("pitch", 50)
                    .param("speed", 50)
                    .param("volume", volume)
                    .build(),
                null,
            )
            if (handle.getCode() != 0) {
                throw IllegalStateException("AIKit XTTS start failed with code=${handle.getCode()}")
            }
            currentHandle.set(handle)
            completion.set(CompletableDeferred())

            withContext(Dispatchers.IO) {
                val payload = AiText.get("text").data(text).valid()
                val ret = AiHelper.getInst().write(
                    AiRequest.builder().payload(payload).build(),
                    handle,
                )
                if (ret != 0) {
                    throw IllegalStateException("AIKit XTTS write failed with code=$ret")
                }
            }
        }

        val deferred = completion.get() ?: return
        withTimeout(60_000L) { deferred.await() }
    }

    override suspend fun stop() {
        mutex.withLock {
            stopInternal()
        }
    }

    private fun registerListenerIfNeeded() {
        if (listenerRegistered) return
        AiHelper.getInst().registerListener(config.aiKitTtsAbilityId, object : AiListener {
            override fun onResult(handleID: Int, list: List<AiResponse>, usrContext: Any?) {
                list.forEach { response ->
                    if (response.getKey() == "audio") {
                        val bytes = response.getValue() ?: return@forEach
                        audioTrack?.write(bytes, 0, bytes.size)
                    }
                }
            }

            override fun onEvent(handleID: Int, event: Int, eventData: List<AiResponse>, usrContext: Any?) {
                when (event) {
                    AeeEvent.AEE_EVENT_START.getValue() -> audioTrack?.play()
                    AeeEvent.AEE_EVENT_END.getValue() -> {
                        currentHandle.getAndSet(null)?.let { AiHelper.getInst().end(it) }
                        audioTrack?.pause()
                        audioTrack?.flush()
                        completion.getAndSet(null)?.complete(Unit)
                    }
                }
            }

            override fun onError(handleID: Int, code: Int, message: String?, usrContext: Any?) {
                completion.getAndSet(null)?.completeExceptionally(
                    IllegalStateException("AIKit XTTS error code=$code message=${message.orEmpty()}")
                )
                currentHandle.getAndSet(null)?.let { AiHelper.getInst().end(it) }
            }
        })
        listenerRegistered = true
    }

    private fun ensureAudioTrack() {
        if (audioTrack != null) return
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
        )
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize,
            AudioTrack.MODE_STREAM,
        )
    }

    private fun stopInternal() {
        completion.getAndSet(null)?.complete(Unit)
        currentHandle.getAndSet(null)?.let { AiHelper.getInst().end(it) }
        audioTrack?.takeIf { it.state == AudioTrack.STATE_INITIALIZED }?.let {
            kotlin.runCatching { it.pause() }
            kotlin.runCatching { it.flush() }
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
}

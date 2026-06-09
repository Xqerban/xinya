package com.xinya.dtx.core.speech

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.iflytek.aikit.core.AiAudio
import com.iflytek.aikit.core.AiHandle
import com.iflytek.aikit.core.AiHelper
import com.iflytek.aikit.core.AiListener
import com.iflytek.aikit.core.AiRequest
import com.iflytek.aikit.core.AiResponse
import com.iflytek.aikit.core.AiStatus
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext

@Singleton
class AikitWakeupEngine @Inject constructor(
    private val initializer: AikitSdkInitializer,
    private val config: IflytekOfflineConfig,
    private val voiceConfig: com.xinya.dtx.core.voice.VoiceInteractionConfig,
) : WakeupEngine {
    private val _events = MutableSharedFlow<WakeupEvent>(extraBufferCapacity = 16)
    override val events: Flow<WakeupEvent> = _events

    private val recording = AtomicBoolean(false)
    private var handle: AiHandle? = null
    private var audioRecord: AudioRecord? = null
    private var listenerRegistered = false

    override suspend fun start() {
        val workDir = initializer.ensureInitialized()
        registerListenerIfNeeded()
        prepareKeywordFile(workDir)

        val loadRet = AiHelper.getInst().loadData(
            config.aiKitWakeupAbilityId,
            AiRequest.builder().customText("key_word", File(workDir, "ivw/keyword.txt").absolutePath, 0).build(),
        )
        if (loadRet != 0) {
            throw IllegalStateException("AIKit wakeup loadData failed with code=$loadRet")
        }

        val specifyRet = AiHelper.getInst().specifyDataSet(
            config.aiKitWakeupAbilityId,
            "key_word",
            intArrayOf(0),
        )
        if (specifyRet != 0) {
            throw IllegalStateException("AIKit wakeup specifyDataSet failed with code=$specifyRet")
        }

        handle = AiHelper.getInst().start(
            config.aiKitWakeupAbilityId,
            AiRequest.builder()
                .param("wdec_param_nCmThreshold", "0 0:800")
                .param("gramLoad", true)
                .build(),
            null,
        ).also {
            if (it.getCode() != 0) {
                throw IllegalStateException("AIKit wakeup start failed with code=${it.getCode()}")
            }
        }

        startRecordingLoop()
    }

    override suspend fun stop() {
        recording.set(false)
        audioRecord?.runCatching { stop() }
        audioRecord?.release()
        audioRecord = null
        handle?.let { AiHelper.getInst().end(it) }
        handle = null
    }

    private fun registerListenerIfNeeded() {
        if (listenerRegistered) return
        AiHelper.getInst().registerListener(config.aiKitWakeupAbilityId, object : AiListener {
            override fun onResult(handleID: Int, outputData: List<AiResponse>, usrContext: Any?) {
                outputData.forEach { response ->
                    val key = response.getKey()
                    // Ignore pre-wakeup noise so the debug flow only reacts to a confirmed wake event.
                    if (key != "func_wake_up") return@forEach
                    val payload = response.getValue()?.decodeToString().orEmpty()
                    val keyword = voiceConfig.allWakeWords().firstOrNull { payload.contains(it) }
                        ?: voiceConfig.primaryWakeWord
                    _events.tryEmit(
                        WakeupEvent(
                            keyword = keyword,
                            isPrimary = keyword == voiceConfig.primaryWakeWord,
                        )
                    )
                }
            }

            override fun onEvent(handleID: Int, event: Int, eventData: List<AiResponse>, usrContext: Any?) = Unit

            override fun onError(handleID: Int, code: Int, message: String?, usrContext: Any?) = Unit
        })
        listenerRegistered = true
    }

    private suspend fun startRecordingLoop() = withContext(Dispatchers.IO) {
        if (recording.get()) return@withContext
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer.coerceAtLeast(BUFFER_SIZE),
        )
        val record = audioRecord ?: return@withContext
        recording.set(true)
        record.startRecording()
        val buffer = ByteArray(BUFFER_SIZE)
        while (recording.get()) {
            val read = record.read(buffer, 0, buffer.size)
            if (read <= 0) continue
            val payload = AiAudio.get("wav")
                .data(buffer.copyOf(read))
                .sampleRate(SAMPLE_RATE)
                .channels(1)
                .bitDepth(16)
                .status(AiStatus.CONTINUE)
                .valid()
            handle?.let {
                AiHelper.getInst().write(
                    AiRequest.builder().payload(payload).build(),
                    it,
                )
            }
        }
    }

    private fun prepareKeywordFile(workDir: File) {
        val ivwDir = File(workDir, "ivw")
        if (!ivwDir.exists() || ivwDir.listFiles().isNullOrEmpty()) {
            throw IllegalStateException(
                "Missing AIKit IVW resources in ${ivwDir.absolutePath}. " +
                    "Please place the official AIKit ivw resource package under app/src/main/assets/iflytek/aikit/ivw."
            )
        }
        File(ivwDir, "keyword.txt").writeText(
            voiceConfig.allWakeWords().joinToString(separator = ";\n", postfix = ";\n")
        )
    }

    private companion object {
        const val SAMPLE_RATE = 16000
        const val BUFFER_SIZE = 1280
    }
}

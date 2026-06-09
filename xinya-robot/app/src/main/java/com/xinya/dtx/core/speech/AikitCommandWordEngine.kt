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
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class AikitCommandWordEngine @Inject constructor(
    private val initializer: AikitSdkInitializer,
    private val config: IflytekOfflineConfig,
) : CommandWordEngine {
    private val mutex = Mutex()
    private val _results = MutableSharedFlow<CommandRecognitionResult>(extraBufferCapacity = 16)
    override val results: Flow<CommandRecognitionResult> = _results
    private val _debugResults = MutableSharedFlow<CommandDebugResult>(extraBufferCapacity = 32)
    override val debugResults: Flow<CommandDebugResult> = _debugResults

    private val recording = AtomicBoolean(false)
    private var currentCommands: List<CommandPhrase> = emptyList()
    private var handle: AiHandle? = null
    private var audioRecord: AudioRecord? = null
    private var listenerRegistered = false
    private var engineInitialized = false
    private var dataLoaded = false
    private var currentGrammarIndex = 0
    private val scope = CoroutineScope(Dispatchers.IO)
    private var recordingJob: Job? = null

    override suspend fun updateGrammar(commands: List<CommandPhrase>) = mutex.withLock {
        currentCommands = commands
            .filter { it.phrase.isNotBlank() }
            .sortedByDescending { it.priority }
            .distinctBy { it.phrase }

        val workDir = initializer.ensureInitialized()
        ensureCommandResources(workDir)
        registerListenerIfNeeded()
        ensureEngineInitialized()
        val grammarFile = buildGrammarFile(workDir, currentCommands)
        _debugResults.tryEmit(
            CommandDebugResult(
                key = "grammar",
                rawText = grammarFile.readText(FSA_CHARSET).replace("\n", "\\n"),
            )
        )
        Unit
    }

    override suspend fun startListening() = mutex.withLock {
        if (recording.get()) return
        if (currentCommands.isEmpty()) {
            throw IllegalStateException("AIKit command grammar is empty. Call updateGrammar() first.")
        }
        initializer.ensureInitialized()
        ensureEngineInitialized()
        registerListenerIfNeeded()
        loadGrammarForCurrentCommands()

        val specifyRet = AiHelper.getInst().specifyDataSet(
            config.aiKitCommandAbilityId,
            "FSA",
            intArrayOf(currentGrammarIndex),
        )
        _debugResults.tryEmit(CommandDebugResult(key = "specifyDataSet", rawText = "code=$specifyRet"))
        if (specifyRet != 0) {
            throw IllegalStateException("AIKit command specifyDataSet failed with code=$specifyRet")
        }

        val aiHandle = AiHelper.getInst().start(
            config.aiKitCommandAbilityId,
            AiRequest.builder()
                .param("languageType", 0)
                .param("vadEndGap", 60)
                .param("vadOn", true)
                .param("beamThreshold", 20)
                .param("hisGramThreshold", 3000)
                .param("vadLinkOn", false)
                .param("vadSpeechEnd", 80)
                .param("vadResponsetime", 1000)
                .param("postprocOn", false)
                .build(),
            null,
        )
        if (aiHandle.getCode() != 0) {
            throw IllegalStateException("AIKit command start failed with code=${aiHandle.getCode()}")
        }
        handle = aiHandle
        _debugResults.tryEmit(CommandDebugResult(key = "start", rawText = "handleCode=${aiHandle.getCode()}"))
        startRecordingLoop()
    }

    override suspend fun stopListening() = mutex.withLock {
        stopInternal()
    }

    private fun registerListenerIfNeeded() {
        if (listenerRegistered) return
        AiHelper.getInst().registerListener(config.aiKitCommandAbilityId, object : AiListener {
            override fun onResult(handleID: Int, outputData: List<AiResponse>, usrContext: Any?) {
                if (outputData.any { it.getStatus() == 2 }) {
                    handle?.let { aiHandle ->
                        val endRet = AiHelper.getInst().end(aiHandle)
                        _debugResults.tryEmit(CommandDebugResult(key = "end", rawText = "code=$endRet"))
                    }
                    recording.set(false)
                    releaseRecorder()
                    handle = null
                }
                outputData.forEach { response ->
                    val key = response.getKey()
                    val raw = decode(response.getValue()).trim()
                    _debugResults.tryEmit(
                        CommandDebugResult(
                            key = "$key.raw",
                            rawText = raw.ifBlank { "<empty>" },
                        )
                    )
                    if (!key.contains("plain") && !key.contains("pgs") && !key.contains("readable")) {
                        return@forEach
                    }
                    val decoded = decodeCommandText(key, response.getValue()).trim()
                    if (decoded.isBlank()) return@forEach
                    _debugResults.tryEmit(CommandDebugResult(key = key, rawText = decoded))
                    val matched = currentCommands.firstOrNull { decoded.contains(it.phrase) } ?: return@forEach
                    _results.tryEmit(
                        CommandRecognitionResult(
                            intentCode = matched.intentCode,
                            phrase = matched.phrase,
                            rawText = decoded,
                        )
                    )
                }
            }

            override fun onEvent(handleID: Int, event: Int, eventData: List<AiResponse>, usrContext: Any?) {
                _debugResults.tryEmit(CommandDebugResult(key = "event", rawText = "event=$event handle=$handleID"))
            }

            override fun onError(handleID: Int, code: Int, message: String?, usrContext: Any?) {
                _debugResults.tryEmit(
                    CommandDebugResult(
                        key = "error",
                        rawText = "code=$code, message=${message.orEmpty()}, handle=$handleID",
                    )
                )
            }
        })
        listenerRegistered = true
    }

    private fun startRecordingLoop() {
        recordingJob?.cancel()
        recordingJob = scope.launch {
        if (USE_OFFICIAL_TEST_AUDIO) {
            startOfficialTestAudioLoop()
            return@launch
        }
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
        val record = audioRecord ?: return@launch
        _debugResults.tryEmit(
            CommandDebugResult(
                key = "record",
                rawText = "minBuffer=$minBuffer, state=${record.state}",
            )
        )
        val buffer = ByteArray(BUFFER_SIZE)
        var status = AiStatus.BEGIN
        recording.set(true)
        record.startRecording()
        _debugResults.tryEmit(
            CommandDebugResult(
                key = "record.start",
                rawText = "recordingState=${record.recordingState}",
            )
        )
        val sessionStartAt = System.currentTimeMillis()
        var firstPositiveReadLogged = false
        while (recording.get()) {
            val read = record.read(buffer, 0, buffer.size)
            if (read <= 0) {
                _debugResults.tryEmit(CommandDebugResult(key = "record.read", rawText = "bytes=$read"))
                continue
            }
            if (!firstPositiveReadLogged) {
                _debugResults.tryEmit(CommandDebugResult(key = "record.read", rawText = "bytes=$read"))
                firstPositiveReadLogged = true
            }
            val outgoingStatus = if (System.currentTimeMillis() - sessionStartAt >= MAX_SESSION_MS) {
                AiStatus.END
            } else {
                status
            }
            val payload = AiAudio.get("audio")
                .data(buffer.copyOf(read))
                .status(outgoingStatus)
                .valid()
            handle?.let {
                val writeRet = AiHelper.getInst().write(
                    AiRequest.builder().payload(payload).build(),
                    it,
                )
                if (outgoingStatus == AiStatus.BEGIN || outgoingStatus == AiStatus.END) {
                    _debugResults.tryEmit(
                        CommandDebugResult(
                            key = "write",
                            rawText = "status=$outgoingStatus, bytes=$read, code=$writeRet",
                        )
                    )
                }
                if (writeRet == 0) {
                    val readRet = AiHelper.getInst().read(config.aiKitCommandAbilityId, it)
                    if (outgoingStatus == AiStatus.BEGIN || outgoingStatus == AiStatus.END || readRet != 0) {
                        _debugResults.tryEmit(
                            CommandDebugResult(
                                key = "read",
                                rawText = "status=$outgoingStatus, code=$readRet",
                            )
                        )
                    }
                } else {
                    _debugResults.tryEmit(
                        CommandDebugResult(
                            key = "write",
                            rawText = "status=$outgoingStatus, bytes=$read, code=$writeRet",
                        )
                    )
                }
            }
            if (outgoingStatus == AiStatus.END) {
                recording.set(false)
                releaseRecorder()
                _debugResults.tryEmit(CommandDebugResult(key = "record.stop", rawText = "autoEnd=true"))
                break
            }
            status = AiStatus.CONTINUE
        }
        }
    }

    private suspend fun startOfficialTestAudioLoop() {
        val workDir = initializer.ensureInitialized()
        val testAudio = File(workDir, "CNENESR/testAudio/cn_test.pcm")
        require(testAudio.isFile) { "Missing official AIKit test audio: ${testAudio.absolutePath}" }

        val aiHandle = handle ?: return
        val bytes = testAudio.readBytes()
        _debugResults.tryEmit(
            CommandDebugResult(
                key = "record.test",
                rawText = "source=official-cn_test.pcm, bytes=${bytes.size}",
            )
        )
        recording.set(true)
        var offset = 0
        while (offset < bytes.size && recording.get()) {
            val end = (offset + BUFFER_SIZE).coerceAtMost(bytes.size)
            val status = when {
                offset == 0 -> AiStatus.BEGIN
                end == bytes.size -> AiStatus.END
                else -> AiStatus.CONTINUE
            }
            val payload = AiAudio.get("audio")
                .data(bytes.copyOfRange(offset, end))
                .status(status)
                .valid()
            val writeRet = AiHelper.getInst().write(AiRequest.builder().payload(payload).build(), aiHandle)
            val readRet = if (writeRet == 0) {
                AiHelper.getInst().read(config.aiKitCommandAbilityId, aiHandle)
            } else {
                writeRet
            }
            if (status == AiStatus.BEGIN || status == AiStatus.END || writeRet != 0 || readRet != 0) {
                _debugResults.tryEmit(
                    CommandDebugResult(
                        key = "test.audio",
                        rawText = "status=$status, write=$writeRet, read=$readRet",
                    )
                )
            }
            offset = end
        }
        recording.set(false)
        _debugResults.tryEmit(CommandDebugResult(key = "record.stop", rawText = "officialTestAudio=true"))
    }

    private fun ensureEngineInitialized() {
        if (engineInitialized) return
        val ret = AiHelper.getInst().engineInit(
            config.aiKitCommandAbilityId,
            AiRequest.builder()
                .param("decNetType", "fsa")
                .param("punishCoefficient", 0.0)
                .param("wfst_addType", 0)
                .build(),
        )
        _debugResults.tryEmit(CommandDebugResult(key = "engineInit", rawText = "code=$ret"))
        if (ret != 0) {
            throw IllegalStateException("AIKit command engineInit failed with code=$ret")
        }
        engineInitialized = true
    }

    private fun unloadGrammarIfNeeded() {
        if (!dataLoaded) return
        val ret = AiHelper.getInst().unLoadData(config.aiKitCommandAbilityId, "FSA", currentGrammarIndex)
        if (ret != 0) {
            throw IllegalStateException("AIKit command unLoadData failed with code=$ret")
        }
        dataLoaded = false
    }

    private suspend fun loadGrammarForCurrentCommands() {
        val workDir = initializer.ensureInitialized()
        unloadGrammarIfNeeded()
        val grammarFile = if (USE_OFFICIAL_TEST_AUDIO) {
            officialGrammarFile(workDir)
        } else {
            buildGrammarFile(workDir, currentCommands)
        }
        val loadRet = AiHelper.getInst().loadData(
            config.aiKitCommandAbilityId,
            AiRequest.builder()
                .customText("FSA", grammarFile.absolutePath, currentGrammarIndex)
                .build(),
        )
        _debugResults.tryEmit(
            CommandDebugResult(
                key = "loadData",
                rawText = "grammar=${grammarFile.absolutePath}, code=$loadRet",
            )
        )
        if (loadRet != 0) {
            throw IllegalStateException("AIKit command loadData failed with code=$loadRet")
        }
        dataLoaded = true
    }

    private fun buildGrammarFile(workDir: File, commands: List<CommandPhrase>): File {
        require(commands.isNotEmpty()) { "Command grammar cannot be empty." }
        val grammarDir = File(workDir, "CNENESR/fsa")
        grammarDir.mkdirs()
        return File(grammarDir, "robot_commands.txt").apply {
            writeText(
                buildString {
                    appendLine("#FSA 1.0;")
                    appendLine("0\t1\t<esr>")
                    appendLine(";")
                    append("<esr>:")
                    append(commands.joinToString("|") { it.phrase })
                    append(";")
                },
                FSA_CHARSET,
            )
        }
    }

    private fun officialGrammarFile(workDir: File): File {
        val file = File(workDir, "CNENESR/fsa/cn_fsa.txt")
        require(file.isFile) { "Missing official AIKit FSA file: ${file.absolutePath}" }
        currentCommands = listOf(
            CommandPhrase("OFFICIAL_OPEN_AC", "打开空调", priority = 100),
            CommandPhrase("OFFICIAL_CLOSE_AC", "关闭空调", priority = 100),
            CommandPhrase("OFFICIAL_OPEN_TV", "打开电视", priority = 100),
            CommandPhrase("OFFICIAL_CLOSE_TV", "关闭电视", priority = 100),
            CommandPhrase("OFFICIAL_ALBUM", "我想听周杰伦的专辑", priority = 100),
        )
        return file
    }

    private fun ensureCommandResources(workDir: File) {
        val dir = File(workDir, "CNENESR")
        if (!dir.exists() || dir.listFiles().isNullOrEmpty()) {
            throw IllegalStateException(
                "Missing AIKit command resources in ${dir.absolutePath}. " +
                    "Please place the official AIKit CNENESR resource package under app/src/main/assets/iflytek/aikit/CNENESR."
            )
        }
    }

    private fun stopInternal() {
        recording.set(false)
        recordingJob?.cancel()
        recordingJob = null
        releaseRecorder()
        handle?.let {
            val payload = AiAudio.get("audio")
                .data(ByteArray(0))
                .status(AiStatus.END)
                .valid()
            kotlin.runCatching {
                AiHelper.getInst().write(AiRequest.builder().payload(payload).build(), it)
            }
            kotlin.runCatching { AiHelper.getInst().end(it) }
        }
        handle = null
    }

    private fun releaseRecorder() {
        audioRecord?.runCatching {
            if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                stop()
            }
        }
        audioRecord?.release()
        audioRecord = null
    }

    private fun decode(bytes: ByteArray?): String {
        if (bytes == null || bytes.isEmpty()) return ""
        return runCatching { String(bytes, Charset.forName("GBK")) }
            .getOrElse { bytes.decodeToString() }
    }

    private fun decodeCommandText(key: String, bytes: ByteArray?): String {
        val decoded = decode(bytes)
        if (!key.contains("readable")) {
            return decoded
        }

        return runCatching {
            val json = JSONObject(decoded)
            val words = json.optJSONArray("ws") ?: JSONArray()
            buildString {
                for (index in 0 until words.length()) {
                    append(words.optJSONObject(index)?.optString("w").orEmpty())
                }
            }.ifBlank { decoded }
        }.getOrElse { decoded }
    }

    private companion object {
        const val SAMPLE_RATE = 16000
        const val BUFFER_SIZE = 1280
        const val MAX_SESSION_MS = 8_000L
        const val USE_OFFICIAL_TEST_AUDIO = false
        val FSA_CHARSET: Charset = Charset.forName("GBK")
    }
}

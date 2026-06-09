package com.xinya.dtx.core.speech

import kotlinx.coroutines.flow.Flow

/**
 * Offline speech abstractions.
 * Business modules depend on these interfaces instead of concrete iFlytek SDKs.
 */
interface WakeupEngine {
    val events: Flow<WakeupEvent>

    suspend fun start()
    suspend fun stop()
}

interface CommandWordEngine {
    suspend fun updateGrammar(commands: List<CommandPhrase>)
    suspend fun startListening()
    suspend fun stopListening()
    val results: Flow<CommandRecognitionResult>
    val debugResults: Flow<CommandDebugResult>
}

interface OfflineIatEngine {
    suspend fun startListening()
    suspend fun stopListening()
    val results: Flow<IatRecognitionResult>
    val volumes: Flow<Int>
}

interface OfflineTtsEngine {
    suspend fun speak(text: String, interruptCurrent: Boolean = false, volume: Int = 50)
    suspend fun stop()
}

data class WakeupEvent(
    val keyword: String,
    val isPrimary: Boolean,
)

data class CommandPhrase(
    val intentCode: String,
    val phrase: String,
    val priority: Int = 0,
)

data class CommandRecognitionResult(
    val intentCode: String,
    val phrase: String,
    val rawText: String,
    val score: Float? = null,
)

data class CommandDebugResult(
    val key: String,
    val rawText: String,
)

data class IatRecognitionResult(
    val text: String,
    val isFinal: Boolean,
    val rawPayload: String? = null,
)

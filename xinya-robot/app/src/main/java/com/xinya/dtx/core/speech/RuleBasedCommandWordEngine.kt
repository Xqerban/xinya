package com.xinya.dtx.core.speech

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleBasedCommandWordEngine @Inject constructor() : CommandWordEngine {
    private val _results = MutableSharedFlow<CommandRecognitionResult>(extraBufferCapacity = 16)
    override val results: Flow<CommandRecognitionResult> = _results
    private val _debugResults = MutableSharedFlow<CommandDebugResult>(extraBufferCapacity = 16)
    override val debugResults: Flow<CommandDebugResult> = _debugResults

    private var commands: List<CommandPhrase> = emptyList()
    private var listening = false

    override suspend fun updateGrammar(commands: List<CommandPhrase>) {
        this.commands = commands
            .filter { it.phrase.isNotBlank() }
            .sortedByDescending { it.priority }
    }

    override suspend fun startListening() {
        listening = true
    }

    override suspend fun stopListening() {
        listening = false
    }

    fun submitTranscript(rawText: String) {
        if (!listening) return
        val normalized = rawText.trim()
        if (normalized.isBlank()) return
        _debugResults.tryEmit(CommandDebugResult(key = "plain", rawText = normalized))
        val matched = commands.firstOrNull { phrase ->
            normalized.contains(phrase.phrase, ignoreCase = true)
        } ?: return

        _results.tryEmit(
            CommandRecognitionResult(
                intentCode = matched.intentCode,
                phrase = matched.phrase,
                rawText = normalized,
            )
        )
    }
}

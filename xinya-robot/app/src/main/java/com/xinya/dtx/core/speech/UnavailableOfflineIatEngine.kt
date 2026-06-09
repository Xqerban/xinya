package com.xinya.dtx.core.speech

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Singleton
class UnavailableOfflineIatEngine @Inject constructor() : OfflineIatEngine {
    override val results: Flow<IatRecognitionResult> = emptyFlow()
    override val volumes: Flow<Int> = emptyFlow()

    override suspend fun startListening() {
        throw IllegalStateException(
            "Offline IAT is not configured. This build keeps only the client-provided AIKit SDK."
        )
    }

    override suspend fun stopListening() = Unit
}

package com.xinya.dtx.core.speech

import android.content.Context
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechUtility
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IflytekMscInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: IflytekOfflineConfig,
) {
    private val initialized = AtomicBoolean(false)

    fun ensureInitialized(): Result<Unit> {
        if (initialized.get()) return Result.success(Unit)
        if (config.mscAppId.isBlank()) {
            return Result.failure(
                IllegalStateException("Missing iFlytek MSC appId for offline speech initialization.")
            )
        }

        val params = buildString {
            append("appid=")
            append(config.mscAppId)
            append(",")
            append(SpeechConstant.ENGINE_MODE)
            append("=")
            append(SpeechConstant.MODE_MSC)
        }
        SpeechUtility.createUtility(context, params)
        initialized.set(true)
        return Result.success(Unit)
    }
}

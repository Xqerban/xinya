package com.xinya.dtx.core.speech

import android.content.Context
import android.util.Log
import com.iflytek.aikit.core.AiHelper
import com.iflytek.aikit.core.BaseLibrary
import com.iflytek.aikit.core.CoreListener
import com.iflytek.aikit.core.ErrType
import com.iflytek.aikit.core.LogLvl
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Singleton
class AikitSdkInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: IflytekOfflineConfig,
    private val resourcePreparer: IflytekResourcePreparer,
) {
    private val tag = "AikitSdkInitializer"
    private val initMutex = Mutex()
    @Volatile
    private var initialized = false
    @Volatile
    private var authCode: Int? = null
    @Volatile
    private var authDeferred: CompletableDeferred<Int>? = null
    @Volatile
    private var listenerRegistered = false

    suspend fun ensureInitialized(): File = initMutex.withLock {
        if (initialized && authCode == 0) {
            return resourcePreparer.getAikitWorkDir()
        }

        validateCredentials()
        val workDir = runCatching { resourcePreparer.prepareAikitResources() }
            .getOrElse { error ->
                throw IllegalStateException(
                    "AIKit resource preparation failed. " +
                        "Official demo expects resources under /sdcard/iflytek/. " +
                        "Original error: ${error.message}",
                    error,
                )
            }
        val logFile = resourcePreparer.getAikitLogFile()
        val traceFile = File(workDir, "aikit-init-trace.txt")
        ensureRequiredAbilityDirs(workDir)
        registerAuthListenerIfNeeded()
        appendTrace(traceFile, "ensureInitialized start")

        val deferred = CompletableDeferred<Int>()
        authDeferred = deferred

        withContext(Dispatchers.IO) {
            logFile.parentFile?.mkdirs()
            appendTrace(traceFile, "setLogInfo path=${logFile.absolutePath}")
            AiHelper.getInst().setLogInfo(LogLvl.VERBOSE, 1, logFile.absolutePath)
            val params = BaseLibrary.Params.builder()
                .appId(config.aiKitAppId)
                .apiKey(config.aiKitApiKey)
                .apiSecret(config.aiKitApiSecret)
                .ability(
                    listOf(
                        config.aiKitWakeupAbilityId,
                        config.aiKitCommandAbilityId,
                        config.aiKitTtsAbilityId,
                    ).filter { it.isNotBlank() }.joinToString(";")
                )
                .workDir(normalizedPath(workDir))
                .build()
            appendTrace(
                traceFile,
                "initEntry appId=${config.aiKitAppId} abilities=${
                    listOf(
                        config.aiKitWakeupAbilityId,
                        config.aiKitCommandAbilityId,
                        config.aiKitTtsAbilityId,
                    ).filter { it.isNotBlank() }.joinToString(";")
                } workDir=${normalizedPath(workDir)}"
            )
            Log.i(
                tag,
                "Initializing AIKit with workDir=${normalizedPath(workDir)}, logFile=${logFile.absolutePath}"
            )
            AiHelper.getInst().initEntry(context, params)
        }

        val code = withTimeout(30_000L) { deferred.await() }
        authCode = code
        appendTrace(traceFile, "auth result code=$code")
        if (code != 0) {
            throw IllegalStateException(
                "AIKit authorization failed with code=$code. " +
                    "This usually means authorization or initialization environment mismatch. " +
                    "Check AIKit log: ${logFile.absolutePath}. " +
                    "Trace: ${traceFile.absolutePath}"
            )
        }
        initialized = true
        appendTrace(traceFile, "initialized success")
        return workDir
    }

    private fun registerAuthListenerIfNeeded() {
        if (listenerRegistered) return
        AiHelper.getInst().registerListener(object : CoreListener {
            override fun onAuthStateChange(type: ErrType, code: Int) {
                Log.i(tag, "onAuthStateChange type=$type code=$code")
                val traceFile = File(resourcePreparer.getAikitWorkDir(), "aikit-init-trace.txt")
                appendTrace(traceFile, "onAuthStateChange type=$type code=$code")
                if (type == ErrType.AUTH) {
                    authCode = code
                    authDeferred?.takeIf { !it.isCompleted }?.complete(code)
                }
            }
        })
        listenerRegistered = true
    }

    private fun validateCredentials() {
        require(config.aiKitAppId.isNotBlank()) { "Missing AIKit appId." }
        require(config.aiKitApiKey.isNotBlank()) { "Missing AIKit apiKey." }
        require(config.aiKitApiSecret.isNotBlank()) { "Missing AIKit apiSecret." }
    }

    private fun ensureRequiredAbilityDirs(workDir: File) {
        val required = listOf("ivw", "xtts", "CNENESR")
        required.forEach { name ->
            val dir = File(workDir, name)
            if (!dir.exists() || dir.listFiles().isNullOrEmpty()) {
                throw IllegalStateException(
                    "Missing AIKit resource directory: ${dir.absolutePath}. " +
                        "Please place the official AIKit resources under app/src/main/assets/iflytek/aikit/$name."
                )
            }
        }
    }

    private fun normalizedPath(file: File): String {
        val path = file.absolutePath
        return if (path.endsWith(File.separator)) path else path + File.separator
    }

    private fun appendTrace(file: File, message: String) {
        runCatching {
            file.parentFile?.mkdirs()
            FileOutputStream(file, true).bufferedWriter().use { writer ->
                writer.appendLine("${System.currentTimeMillis()} $message")
            }
        }.onFailure { error ->
            Log.w(tag, "Failed to append AIKit trace: ${error.message}")
        }
    }
}

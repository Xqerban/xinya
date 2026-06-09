package com.xinya.dtx.core.speech

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IflytekResourcePreparer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun prepareAikitResources(): File {
        val workDir = getAikitWorkDir()
        if (containsPreparedAikitResources(workDir)) {
            return workDir
        }
        copyAssetDirectory(
            assetManager = context.assets,
            assetPath = AIKIT_ASSET_ROOT,
            targetDir = workDir,
        )
        require(containsPreparedAikitResources(workDir)) {
            "AIKit resources are incomplete under ${workDir.absolutePath}"
        }
        return workDir
    }

    fun getAikitWorkDir(): File {
        return preferredIflytekDir()
    }

    fun getAikitLogFile(): File = File(getAikitWorkDir(), "aeeLog.txt")

    private fun containsPreparedAikitResources(workDir: File): Boolean {
        if (!workDir.exists()) return false
        return REQUIRED_FILES.all { relativePath ->
            File(workDir, relativePath).isFile
        }
    }

    private fun preferredIflytekDir(): File {
        val appExternal = context.getExternalFilesDir(null)
        if (appExternal != null) {
            val fallbackDir = File(appExternal, "iflytek")
            if (canPrepareIn(fallbackDir)) {
                Log.i(TAG, "Using app-scoped AIKit dir: ${fallbackDir.absolutePath}")
                return fallbackDir
            }
        }

        val fallbackDir = File(context.filesDir, "iflytek")
        Log.w(TAG, "Falling back to internal AIKit dir: ${fallbackDir.absolutePath}")
        return fallbackDir
    }

    private fun canPrepareIn(dir: File): Boolean {
        if (dir.exists() && containsPreparedAikitResources(dir)) {
            return true
        }
        if (!dir.exists() && !dir.mkdirs()) {
            return false
        }
        val probe = File(dir, ".write_probe")
        return runCatching {
            FileOutputStream(probe).use { output ->
                output.write(byteArrayOf(1))
            }
            true
        }.getOrElse { false }
            .also { probe.delete() }
    }

    private fun copyAssetDirectory(
        assetManager: AssetManager,
        assetPath: String,
        targetDir: File,
    ) {
        val children = assetManager.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            copyAssetFile(assetManager, assetPath, targetDir)
            return
        }

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        children.forEach { child ->
            val childAssetPath = "$assetPath/$child"
            val destination = File(targetDir, child)
            copyAssetDirectory(assetManager, childAssetPath, destination)
        }
    }

    private fun copyAssetFile(
        assetManager: AssetManager,
        assetPath: String,
        targetFile: File,
    ) {
        targetFile.parentFile?.mkdirs()
        assetManager.open(assetPath).use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    companion object {
        private const val TAG = "IflytekResourcePreparer"
        const val AIKIT_ASSET_ROOT = "iflytek/aikit"
        private val REQUIRED_FILES = listOf(
            "ivw/IVW_MLP_1",
            "ivw/IVW_KEYWORD_1",
            "xtts/e05d571cc_1.0.0_xTTS_CnCn_xiaoyan_2018_fix_arm.dat",
            "xtts/e3fe94474_1.0.0_xTTS_CnCn_xiaoyan_2018_arm.irf",
            "CNENESR/e75f07b62_MLP_VAD_CN.bin_1.0.0.0",
            "CNENESR/e75f07b62_WFST_CN.bin_1.0.0.0",
        )
    }
}

package com.xinya.dtx.di

import com.xinya.dtx.BuildConfig
import com.xinya.dtx.core.speech.AikitCommandWordEngine
import com.xinya.dtx.core.speech.AikitOfflineTtsEngine
import com.xinya.dtx.core.speech.AikitWakeupEngine
import com.xinya.dtx.core.speech.IflytekOfflineConfig
import com.xinya.dtx.core.speech.MscOfflineIatEngine
import com.xinya.dtx.core.speech.OfflineIatEngine
import com.xinya.dtx.core.speech.OfflineTtsEngine
import com.xinya.dtx.core.speech.CommandWordEngine
import com.xinya.dtx.core.speech.WakeupEngine
import com.xinya.dtx.core.voice.VoiceInteractionConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VoiceModule {
    @Provides
    @Singleton
    fun provideIflytekOfflineConfig(): IflytekOfflineConfig = IflytekOfflineConfig(
        mscAppId = BuildConfig.IFLYTEK_MSC_APP_ID,
        aiKitAppId = BuildConfig.IFLYTEK_AIKIT_APP_ID,
        aiKitApiKey = BuildConfig.IFLYTEK_AIKIT_API_KEY,
        aiKitApiSecret = BuildConfig.IFLYTEK_AIKIT_API_SECRET,
    )

    @Provides
    @Singleton
    fun provideOfflineIatEngine(engine: MscOfflineIatEngine): OfflineIatEngine = engine

    @Provides
    @Singleton
    fun provideCommandWordEngine(engine: AikitCommandWordEngine): CommandWordEngine = engine

    @Provides
    @Singleton
    fun provideOfflineTtsEngine(engine: AikitOfflineTtsEngine): OfflineTtsEngine = engine

    @Provides
    @Singleton
    fun provideWakeupEngine(engine: AikitWakeupEngine): WakeupEngine = engine

    @Provides
    @Singleton
    fun provideVoiceInteractionConfig(): VoiceInteractionConfig = VoiceInteractionConfig()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}

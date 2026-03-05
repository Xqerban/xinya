package com.xinya.dtx.di

import android.content.Context
import androidx.room.Room
import com.xinya.dtx.core.database.AppDatabase
import com.xinya.dtx.core.database.dao.*
import com.xinya.dtx.core.network.ApiService
import com.xinya.dtx.core.network.interceptor.AuthInterceptor
import com.xinya.dtx.core.network.interceptor.NetworkMonitorInterceptor
import com.xinya.dtx.feature.agent.data.AgentRepository
import com.xinya.dtx.feature.education.data.EducationRepository
import com.xinya.dtx.feature.home.data.PatientRepository
import com.xinya.dtx.feature.hopetree.data.HopeTreeRepository
import com.xinya.dtx.feature.pro.data.ProRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    // ==================== 数据库 ====================
    
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
        .fallbackToDestructiveMigration()
        .build()
    }
    
    @Provides
    fun providePatientDao(database: AppDatabase): PatientDao = database.patientDao()
    
    @Provides
    fun provideConversationDao(database: AppDatabase): ConversationDao = database.conversationDao()
    
    @Provides
    fun provideProRecordDao(database: AppDatabase): ProRecordDao = database.proRecordDao()
    
    @Provides
    fun provideHopeTreeDao(database: AppDatabase): HopeTreeDao = database.hopeTreeDao()
    
    @Provides
    fun provideSyncQueueDao(database: AppDatabase): SyncQueueDao = database.syncQueueDao()
    
    // ==================== 网络 ====================
    
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        networkMonitorInterceptor: NetworkMonitorInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(networkMonitorInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }

    // ==================== Repository ====================

    @Provides
    @Singleton
    fun providePatientRepository(apiService: ApiService): PatientRepository =
        PatientRepository(apiService)

    @Provides
    @Singleton
    fun provideAgentRepository(apiService: ApiService): AgentRepository =
        AgentRepository(apiService)

    @Provides
    @Singleton
    fun provideProRepository(apiService: ApiService): ProRepository =
        ProRepository(apiService)

    @Provides
    @Singleton
    fun provideHopeTreeRepository(apiService: ApiService): HopeTreeRepository =
        HopeTreeRepository(apiService)

    @Provides
    @Singleton
    fun provideEducationRepository(apiService: ApiService): EducationRepository =
        EducationRepository(apiService)
}

/**
 * 构建配置
 * - 模拟器调试：10.0.2.2 是模拟器访问宿主机 localhost 的特殊地址
 * - 真机调试：改为宿主机的局域网 IP（如 10.223.3.195）
 */
object BuildConfig {
    const val API_BASE_URL = "http://10.0.2.2:8080/"
    const val DEBUG = true
}

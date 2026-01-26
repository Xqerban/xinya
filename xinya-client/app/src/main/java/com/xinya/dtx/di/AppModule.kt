package com.xinya.dtx.di

import android.content.Context
import androidx.room.Room
import com.xinya.dtx.core.database.AppDatabase
import com.xinya.dtx.core.database.dao.*
import com.xinya.dtx.core.network.ApiService
import com.xinya.dtx.core.network.interceptor.AuthInterceptor
import com.xinya.dtx.core.network.interceptor.NetworkMonitorInterceptor
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
}

/**
 * 构建配置
 */
object BuildConfig {
    // TODO: 从实际构建配置中获取
    const val API_BASE_URL = "http://localhost:8080/"
    const val DEBUG = true
}

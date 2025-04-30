package org.signal.cashu.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import org.signal.cashu.database.CashuDatabase
import org.signal.cashu.service.CashuService
import org.signal.cashu.service.DefaultCashuService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CashuModule {

    @Provides
    @Singleton
    fun provideCashuDatabase(
        @ApplicationContext context: Context
    ): CashuDatabase {
        return Room.databaseBuilder(
            context,
            CashuDatabase::class.java,
            "cashu_database"
        ).build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    @Provides
    @Singleton
    fun provideCashuService(
        database: CashuDatabase,
        httpClient: OkHttpClient
    ): CashuService {
        return DefaultCashuService(httpClient, database)
    }
}
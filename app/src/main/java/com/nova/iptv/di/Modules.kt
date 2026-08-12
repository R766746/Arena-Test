package com.nova.iptv.di

import android.content.Context
import androidx.room.Room
import coil.ImageLoader
import com.nova.iptv.core.perf.CoilConfig
import com.nova.iptv.data.epg.EpgRepository
import com.nova.iptv.data.epg.EpgRepositoryImpl
import com.nova.iptv.data.local.NovaDatabase
import com.nova.iptv.data.playlist.PlaylistRepository
import com.nova.iptv.data.playlist.PlaylistRepositoryImpl
import com.nova.iptv.data.remote.XtreamApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): NovaDatabase =
        Room.databaseBuilder(context, NovaDatabase::class.java, "nova.db")
            .fallbackToDestructiveMigration()
            .build()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun moshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient {
        val log = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(log)
            .addInterceptor { chain ->
                val req = chain.request()
                val b = req.newBuilder()
                if (req.header("User-Agent") == null) {
                    b.header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 12; SHIELD Android TV) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
                    )
                }
                chain.proceed(b.build())
            }
            .build()
    }

    @Provides
    @Singleton
    fun xtreamApi(client: OkHttpClient, moshi: Moshi): XtreamApi =
        Retrofit.Builder()
            .baseUrl("https://invalid.invalid/") // overridden by @Url
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
            .create(XtreamApi::class.java)

    @Provides
    @Singleton
    fun imageLoader(@ApplicationContext context: Context, client: OkHttpClient): ImageLoader =
        CoilConfig.create(context, client)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun playlists(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    abstract fun epg(impl: EpgRepositoryImpl): EpgRepository
}

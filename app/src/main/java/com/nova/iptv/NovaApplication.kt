package com.nova.iptv

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Build
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.nova.iptv.core.perf.CoilConfig
import com.nova.iptv.core.perf.LowRam
import com.nova.iptv.core.perf.PlayerBudget
import com.nova.iptv.data.epg.EpgRefreshWorker
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.player.PlayerManager
import com.nova.iptv.data.playlist.DemoCatalog
import com.nova.iptv.data.playlist.PlaylistRepository
import com.nova.iptv.data.playlist.PlaylistUpdateWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class NovaApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var imageLoader: ImageLoader
    @Inject lateinit var playerBudget: PlayerBudget
    @Inject lateinit var playerManager: PlayerManager
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var playlists: PlaylistRepository
    @Inject lateinit var demoCatalog: DemoCatalog

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR)
            .build()

    override fun newImageLoader(): ImageLoader = imageLoader

    override fun onCreate() {
        super.onCreate()
        LowRam.init(this)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            installStrictMode()
        }
        appScope.launch {
            runCatching { demoCatalog.ensureSeeded() }
            val s = settings.settings.first()
            PlaylistUpdateWorker.schedule(this@NovaApplication, s.updateOnStart)
            if (s.updateEpgOnStart) {
                EpgRefreshWorker.enqueueNow(this@NovaApplication)
            }
        }
        Timber.i("NOVA started on %s / %s  lowRam=%s mem=%dMB", Build.MODEL, Build.DEVICE, LowRam.isLowRam, LowRam.totalMemMb)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            playerManager.releasePreview()
            playerBudget.releasePreview()
            CoilConfig.onTrimMemory(imageLoader, level)
            Timber.w("onTrimMemory level=%d — dropped preview + Coil memory", level)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        playerManager.releasePreview()
        playerManager.releaseMultiView()
        playerBudget.emergencyReleaseAll()
        imageLoader.memoryCache?.clear()
    }

    private fun installStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build(),
        )
    }
}

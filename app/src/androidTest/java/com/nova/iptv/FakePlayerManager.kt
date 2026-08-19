package com.nova.iptv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nova.iptv.core.perf.PlayerBudget
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.player.PlayerManager
import okhttp3.OkHttpClient

/**
 * Lightweight PlayerManager for compose tests — never actually decodes.
 */
fun FakePlayerManager(): PlayerManager {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    return PlayerManager(
        context = ctx,
        settings = SettingsRepository(ctx),
        budget = PlayerBudget(),
        okHttp = OkHttpClient(),
    )
}

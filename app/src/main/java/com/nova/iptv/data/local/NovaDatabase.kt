package com.nova.iptv.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nova.iptv.data.local.dao.ChannelDao
import com.nova.iptv.data.local.dao.DiagnosticsDao
import com.nova.iptv.data.local.dao.EpgSourceDao
import com.nova.iptv.data.local.dao.EpisodeDao
import com.nova.iptv.data.local.dao.HistoryDao
import com.nova.iptv.data.local.dao.PlaylistDao
import com.nova.iptv.data.local.dao.ProgramDao
import com.nova.iptv.data.local.dao.RecordingDao
import com.nova.iptv.data.local.dao.VodDao
import com.nova.iptv.data.local.dao.XmltvChannelDao
import com.nova.iptv.data.local.entity.ChannelEntity
import com.nova.iptv.data.local.entity.ChannelFts
import com.nova.iptv.data.local.entity.DiagnosticsEntity
import com.nova.iptv.data.local.entity.EpgSourceEntity
import com.nova.iptv.data.local.entity.EpisodeEntity
import com.nova.iptv.data.local.entity.PlaylistEntity
import com.nova.iptv.data.local.entity.ProgramEntity
import com.nova.iptv.data.local.entity.ProgramFts
import com.nova.iptv.data.local.entity.RecordingEntity
import com.nova.iptv.data.local.entity.VodEntity
import com.nova.iptv.data.local.entity.VodFts
import com.nova.iptv.data.local.entity.WatchHistoryEntity
import com.nova.iptv.data.local.entity.XmltvChannelEntity

@Database(
    entities = [
        PlaylistEntity::class,
        ChannelEntity::class,
        ChannelFts::class,
        ProgramEntity::class,
        ProgramFts::class,
        VodEntity::class,
        VodFts::class,
        EpisodeEntity::class,
        RecordingEntity::class,
        WatchHistoryEntity::class,
        EpgSourceEntity::class,
        XmltvChannelEntity::class,
        DiagnosticsEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class NovaDatabase : RoomDatabase() {
    abstract fun playlists(): PlaylistDao
    abstract fun channels(): ChannelDao
    abstract fun programs(): ProgramDao
    abstract fun vod(): VodDao
    abstract fun episodes(): EpisodeDao
    abstract fun recordings(): RecordingDao
    abstract fun history(): HistoryDao
    abstract fun epgSources(): EpgSourceDao
    abstract fun xmltvChannels(): XmltvChannelDao
    abstract fun diagnostics(): DiagnosticsDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE programs ADD COLUMN sourceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE programs ADD COLUMN syncToken TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_programs_sourceId_syncToken " +
                        "ON programs(sourceId, syncToken)",
                )
            }
        }
    }
}

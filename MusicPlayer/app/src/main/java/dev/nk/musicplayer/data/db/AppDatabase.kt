package dev.nk.musicplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Track::class, Playlist::class, PlaylistTrack::class, PlayEvent::class,
        SongAnalysis::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playEventDao(): PlayEventDao
    abstract fun songAnalysisDao(): SongAnalysisDao

    companion object {
        /**
         * v2 adds the song-analysis cache. Unlike a scan, these rows cost network calls to
         * rebuild, so this one is a real migration rather than a destructive rebuild.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `song_analysis` (
                        `trackId` INTEGER NOT NULL,
                        `analyzedAt` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `language` TEXT NOT NULL,
                        `sentiment` TEXT NOT NULL,
                        `mood` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `genre` TEXT NOT NULL,
                        `valence` REAL NOT NULL,
                        `energy` REAL NOT NULL,
                        `themes` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `explicit` INTEGER NOT NULL,
                        `transcript` TEXT NOT NULL,
                        PRIMARY KEY(`trackId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_analysis_mood` ON `song_analysis` (`mood`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_analysis_category` ON `song_analysis` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_analysis_sentiment` ON `song_analysis` (`sentiment`)")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "music.db")
                .addMigrations(MIGRATION_1_2)
                // Backstop for any *other* schema change: the library rescans in seconds.
                .fallbackToDestructiveMigration()
                .build()
    }
}

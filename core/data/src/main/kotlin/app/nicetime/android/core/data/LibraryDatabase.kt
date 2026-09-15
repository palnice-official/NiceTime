package app.nicetime.android.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "watch_progress",
    indices = [Index("providerInstanceId"), Index("updatedAtEpochMs")],
    primaryKeys = ["providerInstanceId", "mediaId", "episodeId"],
)
data class WatchProgressEntity(
    val providerInstanceId: String,
    val mediaId: String,
    val episodeId: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long?,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "watchlist",
    indices = [Index("addedAtEpochMs")],
    primaryKeys = ["providerInstanceId", "mediaId"],
)
data class WatchlistEntity(
    val providerInstanceId: String,
    val mediaId: String,
    val mediaType: String,
    val title: String,
    val posterUrl: String?,
    val addedAtEpochMs: Long,
)

@Entity(
    tableName = "download_metadata",
    indices = [Index("providerInstanceId"), Index("updatedAtEpochMs")],
)
data class DownloadMetadataEntity(
    @androidx.room.PrimaryKey val downloadId: String,
    val providerInstanceId: String,
    val mediaId: String,
    val episodeId: String,
    val releaseId: String,
    val title: String,
    val posterUrl: String?,
    val state: String,
    val updatedAtEpochMs: Long,
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM watch_progress ORDER BY updatedAtEpochMs DESC")
    fun observeProgress(): Flow<List<WatchProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: WatchProgressEntity)

    @Query("DELETE FROM watch_progress")
    suspend fun clearHistory()

    @Query("SELECT * FROM watchlist ORDER BY addedAtEpochMs DESC")
    fun observeWatchlist(): Flow<List<WatchlistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToWatchlist(item: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE providerInstanceId = :providerId AND mediaId = :mediaId")
    suspend fun removeFromWatchlist(providerId: String, mediaId: String)

    @Query("SELECT * FROM download_metadata ORDER BY updatedAtEpochMs DESC")
    fun observeDownloads(): Flow<List<DownloadMetadataEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDownload(item: DownloadMetadataEntity)

    @Query("DELETE FROM download_metadata WHERE downloadId = :downloadId")
    suspend fun removeDownload(downloadId: String)
}

@Database(
    entities = [WatchProgressEntity::class, WatchlistEntity::class, DownloadMetadataEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class NiceTimeDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        @Volatile private var instance: NiceTimeDatabase? = null

        fun get(context: Context): NiceTimeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                NiceTimeDatabase::class.java,
                "nicetime.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `download_metadata` (`downloadId` TEXT NOT NULL, `providerInstanceId` TEXT NOT NULL, `mediaId` TEXT NOT NULL, `episodeId` TEXT NOT NULL, `releaseId` TEXT NOT NULL, `title` TEXT NOT NULL, `posterUrl` TEXT, `state` TEXT NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`downloadId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_metadata_providerInstanceId` ON `download_metadata` (`providerInstanceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_metadata_updatedAtEpochMs` ON `download_metadata` (`updatedAtEpochMs`)")
            }
        }
    }
}

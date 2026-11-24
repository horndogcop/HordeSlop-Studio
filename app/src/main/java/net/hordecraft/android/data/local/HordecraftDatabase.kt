package net.hordecraft.android.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "prompt_history")
data class PromptHistoryEntity(
    @PrimaryKey val id: String,
    val title: String?,
    val prompt: String,
    val negativePrompt: String,
    val model: String,
    val steps: Int,
    val cfg: Float,
    val clipSkip: Int,
    val sampler: String,
    val scheduler: String,
    val timestampMillis: Long,
    val isFavorite: Boolean,
)

@Entity(tableName = "model_defaults")
data class ModelDefaultsEntity(
    @PrimaryKey val modelName: String,
    val steps: Int,
    val sampler: String,
    val scheduler: String,
    val clipSkip: Int,
)

@Entity(tableName = "saved_images")
data class SavedImageEntity(
    @PrimaryKey val id: String,
    val requestId: String,
    val generationId: String,
    val fileUri: String,
    val prompt: String,
    val negativePrompt: String,
    val model: String,
    val seed: String,
    val steps: Int,
    val cfg: Float,
    val clipSkip: Int,
    val sampler: String,
    val scheduler: String,
    val width: Int,
    val height: Int,
    val destinationLabel: String,
    val isFavorite: Boolean,
    val partialFromCancel: Boolean,
    val requestJson: String,
    val statusMetadataJson: String,
    val createdAtMillis: Long,
)

@Entity(tableName = "active_jobs")
data class ActiveJobEntity(
    @PrimaryKey val id: String,
    val requestId: String,
    val prompt: String,
    val negativePrompt: String,
    val modelsJoined: String,
    val status: String,
    val waitTimeSeconds: Int,
    val queuePosition: Int,
    val finished: Int,
    val processing: Int,
    val total: Int,
    val seed: String,
    val steps: Int,
    val cfg: Float,
    val clipSkip: Int,
    val sampler: String,
    val scheduler: String,
    val width: Int,
    val height: Int,
    val submittedPayloadJson: String,
    val errorMessage: String?,
    val createdAtMillis: Long,
    val dismissed: Boolean = false,
    val dismissedAtFinishedCount: Int = 0,
)

@Dao
interface PromptHistoryDao {
    @Query("SELECT * FROM prompt_history ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<PromptHistoryEntity>>

    @Query("SELECT * FROM prompt_history ORDER BY timestampMillis DESC")
    suspend fun getAll(): List<PromptHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PromptHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<PromptHistoryEntity>)

    @Query("UPDATE prompt_history SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean)

    @Query("DELETE FROM prompt_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM prompt_history")
    suspend fun deleteAll()
}

@Dao
interface ModelDefaultsDao {
    @Query("SELECT * FROM model_defaults")
    fun observeAll(): Flow<List<ModelDefaultsEntity>>

    @Query("SELECT * FROM model_defaults")
    suspend fun getAll(): List<ModelDefaultsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ModelDefaultsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ModelDefaultsEntity>)

    @Query("DELETE FROM model_defaults WHERE modelName = :modelName")
    suspend fun deleteByModelName(modelName: String)

    @Query("DELETE FROM model_defaults")
    suspend fun deleteAll()
}

@Dao
interface SavedImageDao {
    @Query("SELECT * FROM saved_images ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<SavedImageEntity>>

    @Query("SELECT * FROM saved_images WHERE generationId = :generationId LIMIT 1")
    suspend fun findByGenerationId(generationId: String): SavedImageEntity?

    @Query("SELECT * FROM saved_images ORDER BY createdAtMillis DESC")
    suspend fun getAll(): List<SavedImageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(image: SavedImageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(images: List<SavedImageEntity>)

    @Query("UPDATE saved_images SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean)

    @Query("DELETE FROM saved_images WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM saved_images")
    suspend fun deleteAll()
}

@Dao
interface ActiveJobDao {
    @Query("SELECT * FROM active_jobs WHERE dismissed = 0 ORDER BY createdAtMillis DESC")
    fun observeVisible(): Flow<List<ActiveJobEntity>>

    @Query("SELECT * FROM active_jobs WHERE (dismissed = 0 AND status IN ('QUEUED', 'PROCESSING', 'PAUSED')) OR dismissed = 1 ORDER BY createdAtMillis ASC")
    suspend fun getTrackedForPolling(): List<ActiveJobEntity>

    @Query("SELECT * FROM active_jobs WHERE dismissed = 1")
    suspend fun getDismissed(): List<ActiveJobEntity>

    @Query("SELECT * FROM active_jobs WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ActiveJobEntity?

    @Query("SELECT * FROM active_jobs ORDER BY createdAtMillis DESC")
    suspend fun getAll(): List<ActiveJobEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: ActiveJobEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(jobs: List<ActiveJobEntity>)

    @Query("UPDATE active_jobs SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE active_jobs SET status = 'PAUSED' WHERE status IN ('QUEUED', 'PROCESSING') AND dismissed = 0")
    suspend fun pauseAllActive()

    @Query("UPDATE active_jobs SET status = 'PROCESSING' WHERE status = 'PAUSED' AND dismissed = 0")
    suspend fun resumeAllPaused()

    @Query("UPDATE active_jobs SET dismissed = 1, dismissedAtFinishedCount = :finishedAtDismiss WHERE id = :id")
    suspend fun dismissById(id: String, finishedAtDismiss: Int)

    @Query("UPDATE active_jobs SET dismissed = 1, dismissedAtFinishedCount = finished WHERE status IN ('COMPLETED', 'CANCELLED_PARTIAL', 'FAILED') AND dismissed = 0")
    suspend fun dismissCompleted()

    @Query("DELETE FROM active_jobs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM active_jobs")
    suspend fun deleteAll()
}

@Database(
    entities = [
        PromptHistoryEntity::class,
        ModelDefaultsEntity::class,
        SavedImageEntity::class,
        ActiveJobEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class HordecraftDatabase : RoomDatabase() {
    abstract fun promptHistoryDao(): PromptHistoryDao
    abstract fun modelDefaultsDao(): ModelDefaultsDao
    abstract fun savedImageDao(): SavedImageDao
    abstract fun activeJobDao(): ActiveJobDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE active_jobs ADD COLUMN dismissed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE active_jobs ADD COLUMN dismissedAtFinishedCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context): HordecraftDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                HordecraftDatabase::class.java,
                "hordecraft.db",
            ).addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
    }
}

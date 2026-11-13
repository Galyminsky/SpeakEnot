package com.galyaminsky.speakenot.data.local

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// Only user-owned entries are persisted. Skyeng response bodies have no write path.
@Entity(tableName = "favorite_eng")
data class EnglishFavoriteEntity(@PrimaryKey val meaningId: Long, val dictionary: String, val addedAt: Long)

@Entity(tableName = "favorite_kz")
data class KazakhFavoriteEntity(@PrimaryKey val meaningId: Long, val dictionary: String, val addedAt: Long)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_eng ORDER BY addedAt DESC, meaningId DESC")
    fun observeEnglish(): Flow<List<EnglishFavoriteEntity>>

    @Query("SELECT * FROM favorite_kz ORDER BY addedAt DESC, meaningId DESC")
    fun observeKazakh(): Flow<List<KazakhFavoriteEntity>>

    @Query("SELECT * FROM favorite_eng WHERE meaningId = :id")
    suspend fun english(id: Long): EnglishFavoriteEntity?

    @Query("SELECT * FROM favorite_kz WHERE meaningId = :id")
    suspend fun kazakh(id: Long): KazakhFavoriteEntity?

    @Query("SELECT MAX(addedAt) FROM favorite_eng")
    suspend fun latestEnglish(): Long?

    @Query("SELECT MAX(addedAt) FROM favorite_kz")
    suspend fun latestKazakh(): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addEnglish(entity: EnglishFavoriteEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addKazakh(entity: KazakhFavoriteEntity): Long

    @Query("DELETE FROM favorite_eng WHERE meaningId = :id")
    suspend fun removeEnglish(id: Long): Int

    @Query("DELETE FROM favorite_kz WHERE meaningId = :id")
    suspend fun removeKazakh(id: Long): Int
}

@Entity(tableName = "history", primaryKeys = ["dictionary", "normalizedKey"])
data class HistoryEntity(val dictionary: String, val normalizedKey: String, val query: String, val searchedAt: Long)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE dictionary = :dictionary ORDER BY searchedAt DESC, normalizedKey ASC LIMIT 5")
    fun observeRecent(dictionary: String): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE dictionary = :dictionary ORDER BY searchedAt DESC, normalizedKey ASC")
    suspend fun all(dictionary: String): List<HistoryEntity>

    @Query("SELECT MAX(searchedAt) FROM history WHERE dictionary = :dictionary")
    suspend fun latest(dictionary: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun record(entity: HistoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restore(entity: HistoryEntity): Long

    @Query("DELETE FROM history WHERE dictionary = :dictionary")
    suspend fun clear(dictionary: String)
}

// Reserved for app-owned cache metadata; Skyeng content is not stored.
@Entity(tableName = "cache_metadata")
data class CacheMetadataEntity(@PrimaryKey val key: String, val checkedAt: Long)

@Database(
    entities = [EnglishFavoriteEntity::class, KazakhFavoriteEntity::class, HistoryEntity::class, CacheMetadataEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favorites(): FavoriteDao
    abstract fun history(): HistoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Stage 3 had no favorites write path; old word keys cannot identify meanings.
                db.execSQL("DROP TABLE IF EXISTS favorite_eng")
                db.execSQL("DROP TABLE IF EXISTS favorite_kz")
                db.execSQL("CREATE TABLE IF NOT EXISTS favorite_eng (meaningId INTEGER NOT NULL PRIMARY KEY, dictionary TEXT NOT NULL, addedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS favorite_kz (meaningId INTEGER NOT NULL PRIMARY KEY, dictionary TEXT NOT NULL, addedAt INTEGER NOT NULL)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Earlier stages had no history write path; old rows have no dictionary key.
                db.execSQL("DROP TABLE IF EXISTS history")
                db.execSQL("CREATE TABLE IF NOT EXISTS history (dictionary TEXT NOT NULL, normalizedKey TEXT NOT NULL, query TEXT NOT NULL, searchedAt INTEGER NOT NULL, PRIMARY KEY(dictionary, normalizedKey))")
            }
        }
    }
}

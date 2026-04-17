package com.rfid.inventory.data.local

import android.content.Context
import androidx.room.*

// ─── Entity: pending scan (guardado local cuando no hay red) ──────────────────

@Entity(tableName = "pending_scans")
data class PendingScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val epc: String,
    val readerId: String,
    val locationId: Int?,
    val action: String,
    val rssi: Float?,
    val createdAt: Long = System.currentTimeMillis()
)

// ─── Entity: scan history (cache local de lecturas ya enviadas) ───────────────

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey val id: Int,
    val epc: String,
    val productName: String?,
    val action: String,
    val locationId: Int?,
    val scannedAt: String
)

// ─── DAOs ─────────────────────────────────────────────────────────────────────

@Dao
interface PendingScanDao {

    @Insert
    suspend fun insert(scan: PendingScanEntity): Long

    @Query("SELECT * FROM pending_scans ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingScanEntity>

    @Query("SELECT COUNT(*) FROM pending_scans")
    suspend fun count(): Int

    @Delete
    suspend fun delete(scan: PendingScanEntity)

    @Query("DELETE FROM pending_scans")
    suspend fun deleteAll()
}

@Dao
interface ScanHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(scans: List<ScanHistoryEntity>)

    @Query("SELECT * FROM scan_history ORDER BY scannedAt DESC LIMIT 100")
    suspend fun getRecent(): List<ScanHistoryEntity>

    @Query("DELETE FROM scan_history")
    suspend fun clear()
}

// ─── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [PendingScanEntity::class, ScanHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pendingScanDao(): PendingScanDao
    abstract fun scanHistoryDao(): ScanHistoryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rfid_local.db"
                ).build().also { instance = it }
            }
    }
}

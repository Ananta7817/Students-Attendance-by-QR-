package com.example.data.local

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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pending_attendance")
data class PendingAttendanceEntity(
    @PrimaryKey val deterministicId: String,
    val schoolId: String,
    val studentId: String,
    val studentIdCode: String,
    val studentName: String,
    val studentPhotoUrl: String,
    val classId: String,
    val className: String,
    val section: String,
    val rollNumber: String,
    val attendanceDate: String,
    val attendanceTime: String,
    val status: String,
    val recordedByUid: String,
    val recordedByName: String,
    val adminUid: String,
    val memberUidsCsv: String,
    val queuedAtMillis: Long = System.currentTimeMillis(),
)

@Dao
interface PendingAttendanceDao {
    @Query("SELECT * FROM pending_attendance WHERE schoolId = :schoolId ORDER BY queuedAtMillis DESC")
    fun observePendingForSchool(schoolId: String): Flow<List<PendingAttendanceEntity>>

    @Query("SELECT * FROM pending_attendance WHERE schoolId = :schoolId ORDER BY queuedAtMillis ASC")
    suspend fun getPendingForSchoolOnce(schoolId: String): List<PendingAttendanceEntity>

    @Query("SELECT * FROM pending_attendance WHERE deterministicId = :deterministicId LIMIT 1")
    suspend fun getById(deterministicId: String): PendingAttendanceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: PendingAttendanceEntity): Long

    @Query("DELETE FROM pending_attendance WHERE deterministicId = :deterministicId")
    suspend fun deleteById(deterministicId: String)

    @Query("DELETE FROM pending_attendance WHERE schoolId = :schoolId")
    suspend fun clearSchool(schoolId: String)
}

@Database(entities = [PendingAttendanceEntity::class], version = 1, exportSchema = false)
abstract class OfflineAttendanceDatabase : RoomDatabase() {
    abstract fun pendingAttendanceDao(): PendingAttendanceDao

    companion object {
        @Volatile
        private var INSTANCE: OfflineAttendanceDatabase? = null

        fun getInstance(context: Context): OfflineAttendanceDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OfflineAttendanceDatabase::class.java,
                    "sabqr_offline_attendance.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

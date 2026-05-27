package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.UploadJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadJobDao {

    @Query("""
        SELECT * FROM upload_jobs 
        ORDER BY createdAt DESC
    """)
    fun getAllJobsFlow(): Flow<List<UploadJobEntity>>

    @Query("""
        SELECT * FROM upload_jobs 
        WHERE status IN ('SUCCESS', 'CANCELED') AND completedAt IS NOT NULL 
        ORDER BY completedAt DESC
    """)
    fun getHistoryFlow(): Flow<List<UploadJobEntity>>

    @Query("SELECT * FROM upload_jobs WHERE id = :id")
    suspend fun getJobById(id: String): UploadJobEntity?

    @Query("SELECT * FROM upload_jobs WHERE status = 'QUEUED' ORDER BY createdAt ASC LIMIT 1")
    suspend fun getNextQueuedJob(): UploadJobEntity?

    @Query("UPDATE upload_jobs SET status = 'QUEUED', updatedAt = :now WHERE status = 'UPLOADING'")
    suspend fun resetUploadingJobsToQueued(now: Long)

    @Query("""
        UPDATE upload_jobs
        SET status = 'CANCELED',
            errorMessage = :message,
            updatedAt = :now,
            completedAt = :now
        WHERE id = :id
    """)
    suspend fun markJobCanceled(id: String, message: String, now: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: UploadJobEntity)

    @Update
    suspend fun updateJob(job: UploadJobEntity)

    @Query("DELETE FROM upload_jobs WHERE id = :id")
    suspend fun deleteJobById(id: String)

    @Query("DELETE FROM upload_jobs WHERE status IN ('SUCCESS', 'CANCELED')")
    suspend fun clearHistory()
}

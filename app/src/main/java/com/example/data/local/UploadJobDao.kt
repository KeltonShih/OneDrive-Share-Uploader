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
        WHERE status IN ('COPYING', 'QUEUED', 'UPLOADING', 'FAILED') 
        ORDER BY 
            CASE status 
                WHEN 'UPLOADING' THEN 1 
                WHEN 'COPYING' THEN 2 
                WHEN 'QUEUED' THEN 3 
                WHEN 'FAILED' THEN 4 
                ELSE 5 
            END, 
            updatedAt DESC
    """)
    fun getActiveQueueFlow(): Flow<List<UploadJobEntity>>

    @Query("""
        SELECT * FROM upload_jobs 
        WHERE status IN ('SUCCESS', 'CANCELED', 'FAILED') AND completedAt IS NOT NULL 
        ORDER BY completedAt DESC
    """)
    fun getHistoryFlow(): Flow<List<UploadJobEntity>>

    @Query("SELECT * FROM upload_jobs WHERE id = :id")
    suspend fun getJobById(id: String): UploadJobEntity?

    @Query("SELECT * FROM upload_jobs WHERE status = 'QUEUED' LIMIT 1")
    suspend fun getNextQueuedJob(): UploadJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: UploadJobEntity)

    @Update
    suspend fun updateJob(job: UploadJobEntity)

    @Query("DELETE FROM upload_jobs WHERE id = :id")
    suspend fun deleteJobById(id: String)

    @Query("DELETE FROM upload_jobs WHERE status IN ('SUCCESS', 'CANCELED')")
    suspend fun clearHistory()
}

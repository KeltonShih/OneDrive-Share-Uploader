package com.example.data.repository

import com.example.data.local.UploadJobDao
import com.example.data.model.UploadJobEntity
import kotlinx.coroutines.flow.Flow

class UploadRepository(private val uploadJobDao: UploadJobDao) {
    val allJobs: Flow<List<UploadJobEntity>> = uploadJobDao.getAllJobsFlow()
    val history: Flow<List<UploadJobEntity>> = uploadJobDao.getHistoryFlow()

    suspend fun getJobById(id: String): UploadJobEntity? = uploadJobDao.getJobById(id)
    
    suspend fun insertJob(job: UploadJobEntity) = uploadJobDao.insertJob(job)
    
    suspend fun updateJob(job: UploadJobEntity) = uploadJobDao.updateJob(job)
    
    suspend fun deleteJobById(id: String) = uploadJobDao.deleteJobById(id)
    
    suspend fun clearHistory() = uploadJobDao.clearHistory()

    suspend fun getNextQueuedJob(): UploadJobEntity? = uploadJobDao.getNextQueuedJob()

    suspend fun resetUploadingJobsToQueued(now: Long) = uploadJobDao.resetUploadingJobsToQueued(now)

    suspend fun markJobCanceled(id: String, message: String, now: Long) =
        uploadJobDao.markJobCanceled(id, message, now)
}

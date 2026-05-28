package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.UploadJobEntity

@Database(entities = [UploadJobEntity::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun uploadJobDao(): UploadJobDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE upload_jobs ADD COLUMN uploadedFileName TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE upload_jobs ADD COLUMN destinationId TEXT NOT NULL DEFAULT 'default-upload'")
                db.execSQL("ALTER TABLE upload_jobs ADD COLUMN destinationName TEXT NOT NULL DEFAULT 'Upload'")
                db.execSQL("ALTER TABLE upload_jobs ADD COLUMN driveAccountId TEXT")
                db.execSQL("ALTER TABLE upload_jobs ADD COLUMN driveAccountLabel TEXT DEFAULT 'Current Account'")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "onedrive_uploader_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

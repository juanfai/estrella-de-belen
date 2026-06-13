package com.estrelladebelen.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.estrelladebelen.app.data.local.dao.MeditationDao
import com.estrelladebelen.app.data.local.entity.DownloadedMeditation

@Database(entities = [DownloadedMeditation::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun meditationDao(): MeditationDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "estrella_db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}

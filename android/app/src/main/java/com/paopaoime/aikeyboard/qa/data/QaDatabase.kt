package com.paopaoime.aikeyboard.qa.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [QaSessionEntity::class], version = 3, exportSchema = false)
abstract class QaDatabase : RoomDatabase() {
    abstract fun qaSessionDao(): QaSessionDao

    companion object {
        @Volatile
        private var INSTANCE: QaDatabase? = null

        fun getInstance(context: Context): QaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QaDatabase::class.java,
                    "qa_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

package ru.ama.whereme.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [LocationDbModel::class], version = 1, exportSchema = false)
//@TypeConverters(AnswersConverter::class,CorrectConverter::class)
abstract class AppDatabase : RoomDatabase() {
    companion object {

        private var db: AppDatabase? = null
        private const val DB_NAME = "main.db"
        private val LOCK = Any()

        fun getInstance(context: Context): AppDatabase {
            synchronized(LOCK) {
                db?.let { return it }
                val instance =
                    Room.databaseBuilder(
                        context,
                        AppDatabase::class.java,
                        DB_NAME
                    )
                        .fallbackToDestructiveMigration()
                       // .allowMainThreadQueries()
                        .build()
                db = instance
                return instance
            }
        }
    }

    abstract fun locationDao(): LocationDao
}
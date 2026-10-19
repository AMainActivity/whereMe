package ru.ama.whereme.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [LocationDbModel::class, LegacyHistory::class], version = 4, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tab_locations ADD COLUMN ownerKey TEXT")
                db.execSQL("CREATE INDEX index_tab_locations_datestart ON tab_locations(datestart)")
                db.execSQL("CREATE INDEX index_tab_locations_ownerKey_isWrite ON tab_locations(ownerKey, isWrite)")
                // One durable boundary for assigning the pre-v4 history. New anonymous rows stay local.
                db.execSQL("CREATE TABLE IF NOT EXISTS legacy_history (id INTEGER NOT NULL PRIMARY KEY, lastRowId INTEGER NOT NULL)")
                db.execSQL("INSERT INTO legacy_history SELECT 1, COALESCE(MAX(_id), 0) FROM tab_locations")
            }
        }
    }
}

package ru.ama.whereme.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Present only until the v3 history has been assigned to the profile saved on upgrade. */
@Entity(tableName = "legacy_history")
data class LegacyHistory(@PrimaryKey val id: Int, val lastRowId: Long)

package com.etatech.hashiya.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.etatech.hashiya.core.database.dao.PaperDao
import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperEntity

@Database(
    entities = [PaperEntity::class, PaperAuthorEntity::class],
    version = 1,
    exportSchema = true
)
abstract class HashiyaDatabase : RoomDatabase() {
    abstract fun paperDao(): PaperDao
}

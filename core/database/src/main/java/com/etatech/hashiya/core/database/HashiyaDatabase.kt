package com.etatech.hashiya.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.etatech.hashiya.core.database.dao.PaperDao
import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.PaperSearchEntity

@Database(
    entities = [PaperEntity::class, PaperAuthorEntity::class, PaperSearchEntity::class],
    version = 2,
    exportSchema = true
)
abstract class HashiyaDatabase : RoomDatabase() {
    abstract fun paperDao(): PaperDao
}

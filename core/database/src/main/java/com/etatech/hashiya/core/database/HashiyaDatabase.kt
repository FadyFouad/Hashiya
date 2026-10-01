package com.etatech.hashiya.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.etatech.hashiya.core.database.dao.CitationDao
import com.etatech.hashiya.core.database.dao.CollectionDao
import com.etatech.hashiya.core.database.dao.PaperDao
import com.etatech.hashiya.core.database.model.CollectionEntity
import com.etatech.hashiya.core.database.model.CollectionPaperEntity
import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.PaperNotesEntity
import com.etatech.hashiya.core.database.model.PaperSearchEntity

@Database(
    entities = [
        PaperEntity::class,
        PaperAuthorEntity::class,
        PaperSearchEntity::class,
        PaperNotesEntity::class,
        CollectionEntity::class,
        CollectionPaperEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class HashiyaDatabase : RoomDatabase() {
    abstract fun paperDao(): PaperDao

    abstract fun collectionDao(): CollectionDao

    abstract fun citationDao(): CitationDao
}

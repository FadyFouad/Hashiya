package com.etatech.hashiya.core.database.model

/** What [com.etatech.hashiya.core.database.dao.PaperDao.deleteByOpenAlexId] deleted, so it can be restored. */
data class DeletedPaper(val paper: PaperWithAuthors, val notes: PaperNotesEntity?)

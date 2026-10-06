package com.etatech.hashiya.review

import android.content.Context
import androidx.core.content.edit
import com.etatech.hashiya.core.review.ReviewCounterStore
import com.etatech.hashiya.core.review.ReviewCounters

/** The rating counters, in a private preferences file the backup rules leave out (they include only database/ and datastore/). */
class SharedPreferencesReviewCounterStore(context: Context) : ReviewCounterStore {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun read() = ReviewCounters(
        firstOpenedAt = prefs.getLong(FIRST_OPENED_AT, -1).takeIf { it >= 0 },
        saves = prefs.getInt(SAVES, 0),
        exported = prefs.getBoolean(EXPORTED, false),
        lastAskedAt = prefs.getLong(LAST_ASKED_AT, -1).takeIf { it >= 0 }
    )

    override fun markOpened(now: Long) {
        if (!prefs.contains(FIRST_OPENED_AT)) prefs.edit { putLong(FIRST_OPENED_AT, now) }
    }

    override fun addSave() = prefs.edit { putInt(SAVES, prefs.getInt(SAVES, 0) + 1) }

    override fun markExported() = prefs.edit { putBoolean(EXPORTED, true) }

    override fun markAsked(now: Long) = prefs.edit { putLong(LAST_ASKED_AT, now) }

    private companion object {
        const val FILE = "review_prompt"
        const val FIRST_OPENED_AT = "first_opened_at"
        const val SAVES = "saves"
        const val EXPORTED = "exported"
        const val LAST_ASKED_AT = "last_asked_at"
    }
}

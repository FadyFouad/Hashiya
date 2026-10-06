package com.etatech.hashiya.review

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Google Play's in-app rating sheet. Play decides whether it actually appears (never for apps not installed from Play, and
 * only a few times a year); failures are ignored.
 */
fun Activity.launchPlayReview() {
    val manager = ReviewManagerFactory.create(this)
    manager.requestReviewFlow().addOnCompleteListener { task ->
        if (task.isSuccessful && !isFinishing && !isDestroyed) manager.launchReviewFlow(this, task.result)
    }
}

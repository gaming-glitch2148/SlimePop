package com.slimepop.asmr

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object ReviewHelper {
    fun maybeAskForReview(activity: Activity, streak: Int, totalPops: Int) {
        val today = LocalDate.now()
        val lastIso = Prefs.getReviewLastIso(activity)
        val last = lastIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        val milestone = (streak >= 3) || (totalPops >= 200)
        val cooldownOk = last == null || ChronoUnit.DAYS.between(last, today) >= 30

        if (!milestone || !cooldownOk) return

        Prefs.setReviewLastIso(activity, today.toString())

        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val info = task.result
                manager.launchReviewFlow(activity, info)
            }
        }
    }
}
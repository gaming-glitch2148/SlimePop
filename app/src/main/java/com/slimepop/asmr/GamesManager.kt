package com.slimepop.asmr

import android.app.Activity
import android.util.Log
import com.google.android.gms.games.PlayGames

object GamesManager {

    // Real Leaderboard ID from Play Console
    private const val LEADERBOARD_ID = "CgkInKeujLAcEAIQAQ"

    // Optional: Create an Achievement in Console and paste ID here
    private const val ACHIEVE_100_POPS = "CgkI_example_id"

    fun submitScore(activity: Activity, score: Int) {
        if (score <= 0) return
        PlayGames.getLeaderboardsClient(activity)
            .submitScore(LEADERBOARD_ID, score.toLong())
        
        // Example: Unlock achievement at 100 pops
        if (score >= 100) {
            unlockAchievement(activity, ACHIEVE_100_POPS)
        }
    }

    fun unlockAchievement(activity: Activity, id: String) {
        PlayGames.getAchievementsClient(activity).unlock(id)
    }

    fun showLeaderboard(activity: Activity) {
        PlayGames.getLeaderboardsClient(activity)
            .getLeaderboardIntent(LEADERBOARD_ID)
            .addOnSuccessListener { intent ->
                activity.startActivityForResult(intent, 9004)
            }
            .addOnFailureListener { e ->
                Log.e("GamesManager", "Failed to show leaderboard", e)
                Stripe.show(activity, "Play Games sign-in required.")
            }
    }
    
    fun showAchievements(activity: Activity) {
        PlayGames.getAchievementsClient(activity)
            .achievementsIntent
            .addOnSuccessListener { intent ->
                activity.startActivityForResult(intent, 9005)
            }
    }
}

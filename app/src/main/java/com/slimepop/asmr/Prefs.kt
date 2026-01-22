package com.slimepop.asmr

import android.content.Context

object Prefs {
    private const val FILE = "slime_pop_prefs"

    private const val K_COINS = "coins"
    private const val K_SOUND = "sound"
    private const val K_EQUIPPED_SKIN = "equipped_skin_id"
    private const val K_EQUIPPED_SOUND = "equipped_sound_id"

    private const val K_ADS_REMOVED = "ads_removed"
    private const val K_OWNED_IAP_CSV = "owned_iap_csv"

    private const val K_DAILY_DATE = "daily_claim_date"
    private const val K_DAILY_STREAK = "daily_streak"

    private const val K_BOOST_UNTIL_MS = "boost_until_ms"
    private const val K_POP_SINCE_INT = "pop_since_int"
    private const val K_LAST_INT_MS = "last_int_ms"

    private const val K_LAUNCH_COUNT = "launch_count"
    private const val K_UPSELL_LAST_ISO = "upsell_last_iso"

    private const val K_TOTAL_POPS = "total_pops"
    private const val K_TOTAL_HOLD_MS = "total_hold_ms"

    private const val K_REVIEW_LAST_ISO = "review_last_iso"

    // Quests
    private const val K_QUEST_DATE = "quest_date"
    private const val K_QUEST_PROGRESS_CSV = "quest_progress_csv"
    private const val K_QUEST_CLAIMED_CSV = "quest_claimed_csv"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getCoins(ctx: Context) = sp(ctx).getInt(K_COINS, 0)
    fun setCoins(ctx: Context, v: Int) = sp(ctx).edit().putInt(K_COINS, v).apply()

    fun getSound(ctx: Context) = sp(ctx).getBoolean(K_SOUND, true)
    fun setSound(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_SOUND, v).apply()

    fun getEquippedSkinId(ctx: Context) = sp(ctx).getString(K_EQUIPPED_SKIN, "skin_001") ?: "skin_001"
    fun setEquippedSkinId(ctx: Context, v: String) = sp(ctx).edit().putString(K_EQUIPPED_SKIN, v).apply()

    fun getEquippedSoundId(ctx: Context) = sp(ctx).getString(K_EQUIPPED_SOUND, "sound_001") ?: "sound_001"
    fun setEquippedSoundId(ctx: Context, v: String) = sp(ctx).edit().putString(K_EQUIPPED_SOUND, v).apply()

    fun getAdsRemoved(ctx: Context) = sp(ctx).getBoolean(K_ADS_REMOVED, false)
    fun setAdsRemoved(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_ADS_REMOVED, v).apply()

    fun getOwnedIapCsv(ctx: Context) = sp(ctx).getString(K_OWNED_IAP_CSV, "") ?: ""
    fun setOwnedIapCsv(ctx: Context, v: String) = sp(ctx).edit().putString(K_OWNED_IAP_CSV, v).apply()

    fun getDailyClaimDate(ctx: Context) = sp(ctx).getString(K_DAILY_DATE, null)
    fun setDailyClaimDate(ctx: Context, iso: String) = sp(ctx).edit().putString(K_DAILY_DATE, iso).apply()

    fun getDailyStreak(ctx: Context) = sp(ctx).getInt(K_DAILY_STREAK, 0)
    fun setDailyStreak(ctx: Context, v: Int) = sp(ctx).edit().putInt(K_DAILY_STREAK, v).apply()

    fun getBoostUntilMs(ctx: Context) = sp(ctx).getLong(K_BOOST_UNTIL_MS, 0L)
    fun setBoostUntilMs(ctx: Context, v: Long) = sp(ctx).edit().putLong(K_BOOST_UNTIL_MS, v).apply()

    fun getPopSinceInt(ctx: Context) = sp(ctx).getInt(K_POP_SINCE_INT, 0)
    fun setPopSinceInt(ctx: Context, v: Int) = sp(ctx).edit().putInt(K_POP_SINCE_INT, v).apply()

    fun getLastIntMs(ctx: Context) = sp(ctx).getLong(K_LAST_INT_MS, 0L)
    fun setLastIntMs(ctx: Context, v: Long) = sp(ctx).edit().putLong(K_LAST_INT_MS, v).apply()

    fun getLaunchCount(ctx: Context) = sp(ctx).getInt(K_LAUNCH_COUNT, 0)
    fun setLaunchCount(ctx: Context, v: Int) = sp(ctx).edit().putInt(K_LAUNCH_COUNT, v).apply()

    fun getUpsellLastIso(ctx: Context) = sp(ctx).getString(K_UPSELL_LAST_ISO, null)
    fun setUpsellLastIso(ctx: Context, iso: String) = sp(ctx).edit().putString(K_UPSELL_LAST_ISO, iso).apply()

    fun getTotalPops(ctx: Context) = sp(ctx).getInt(K_TOTAL_POPS, 0)
    fun setTotalPops(ctx: Context, v: Int) = sp(ctx).edit().putInt(K_TOTAL_POPS, v).apply()

    fun getTotalHoldMs(ctx: Context) = sp(ctx).getLong(K_TOTAL_HOLD_MS, 0L)
    fun setTotalHoldMs(ctx: Context, v: Long) = sp(ctx).edit().putLong(K_TOTAL_HOLD_MS, v).apply()

    fun getReviewLastIso(ctx: Context) = sp(ctx).getString(K_REVIEW_LAST_ISO, null)
    fun setReviewLastIso(ctx: Context, iso: String) = sp(ctx).edit().putString(K_REVIEW_LAST_ISO, iso).apply()

    fun getQuestDate(ctx: Context) = sp(ctx).getString(K_QUEST_DATE, null)
    fun setQuestDate(ctx: Context, iso: String) = sp(ctx).edit().putString(K_QUEST_DATE, iso).apply()

    fun getQuestProgressCsv(ctx: Context) = sp(ctx).getString(K_QUEST_PROGRESS_CSV, "") ?: ""
    fun setQuestProgressCsv(ctx: Context, v: String) = sp(ctx).edit().putString(K_QUEST_PROGRESS_CSV, v).apply()

    fun getQuestClaimedCsv(ctx: Context) = sp(ctx).getString(K_QUEST_CLAIMED_CSV, "") ?: ""
    fun setQuestClaimedCsv(ctx: Context, v: String) = sp(ctx).edit().putString(K_QUEST_CLAIMED_CSV, v).apply()
}
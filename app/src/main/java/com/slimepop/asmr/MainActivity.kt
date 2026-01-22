package com.slimepop.asmr

import android.media.AudioAttributes
import android.media.SoundPool
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.slimepop.asmr.databinding.ActivityMainBinding
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class MainActivity : AppCompatActivity() {

    private lateinit var vb: ActivityMainBinding

    private var coins = 0
    private var soundEnabled = true
    private var equippedSkinId = "skin_001"
    private var equippedSoundId = "sound_001"

    private var boostUntilMs: Long = 0L

    private var entitlements: Entitlements = EntitlementResolver.resolveFromOwnedProducts(emptySet())

    private lateinit var billing: BillingManager
    private lateinit var ads: AdManager
    private lateinit var soundscape: SoundscapeManager

    private var soundPool: SoundPool? = null
    private var popSoundId: Int = 0

    private var questState: QuestState? = null

    private val REQ_SHOP = 2001
    private val boostDurationMs = 60_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vb.root)

        coins = Prefs.getCoins(this)
        soundEnabled = Prefs.getSound(this)
        equippedSkinId = Prefs.getEquippedSkinId(this)
        equippedSoundId = Prefs.getEquippedSoundId(this)
        boostUntilMs = Prefs.getBoostUntilMs(this)

        val ownedCsv = Prefs.getOwnedIapCsv(this)
        val ownedSet = EntitlementResolver.ownedSetFromCsv(ownedCsv)
        entitlements = EntitlementResolver.resolveFromOwnedProducts(ownedSet)

        questState = QuestManager.loadOrInit(this)

        setupPopSound()
        soundscape = SoundscapeManager(this)

        ads = AdManager(this, this)
        ads.adsEnabled = !entitlements.adsRemoved
        ads.init()

        billing = BillingManager(this) { ent ->
            entitlements = ent
            applyEntitlements()
        }
        billing.start()

        vb.btnToggleSound.text = if (soundEnabled) "Sound: ON" else "Sound: OFF"

        applyEntitlements()
        applyEquippedSkinToView()
        applySoundscape()

        vb.slimeView.onPop = { baseCoins, holdMs ->
            val multiplier = if (!entitlements.adsRemoved && SystemClock.elapsedRealtime() < boostUntilMs) 2 else 1
            val earned = baseCoins * multiplier

            coins += earned
            Prefs.setCoins(this, coins)

            Prefs.setTotalPops(this, Prefs.getTotalPops(this) + 1)
            Prefs.setTotalHoldMs(this, Prefs.getTotalHoldMs(this) + holdMs)

            questState = QuestManager.applyPop(this, questState ?: QuestManager.loadOrInit(this), 1)
            questState = QuestManager.applyHoldMs(this, questState!!, holdMs)

            if (soundEnabled && popSoundId != 0) {
                soundPool?.play(popSoundId, 0.6f, 0.6f, 1, 0, 1.0f)
            }

            ads.incrementPopAndMaybeShowInterstitial()

            updateTopUI()

            ReviewHelper.maybeAskForReview(
                activity = this,
                streak = Prefs.getDailyStreak(this),
                totalPops = Prefs.getTotalPops(this)
            )
        }

        vb.btnToggleSound.setOnClickListener {
            soundEnabled = !soundEnabled
            Prefs.setSound(this, soundEnabled)
            vb.btnToggleSound.text = if (soundEnabled) "Sound: ON" else "Sound: OFF"
            applySoundscape()
        }

        vb.btnDaily.setOnClickListener { showDailyAndQuestsDialog() }

        vb.btnShop.setOnClickListener {
            val i = android.content.Intent(this, ShopActivity::class.java)
                .putExtra(ShopActivity.EXTRA_OWNED_PRODUCTS_CSV, Prefs.getOwnedIapCsv(this))
                .putExtra(ShopActivity.EXTRA_EQUIPPED_SKIN, equippedSkinId)
                .putExtra(ShopActivity.EXTRA_EQUIPPED_SOUND, equippedSoundId)
            startActivityForResult(i, REQ_SHOP)
        }

        vb.btnRewardedBoost.setOnClickListener {
            if (entitlements.adsRemoved) {
                toast("Ad-free ✅")
                return@setOnClickListener
            }
            ads.showRewarded(
                onReward = {
                    boostUntilMs = SystemClock.elapsedRealtime() + boostDurationMs
                    Prefs.setBoostUntilMs(this, boostUntilMs)
                    updateTopUI()
                    toast("2× coins for 60s")
                },
                onNotReady = { toast("Ad not ready (or offline)") }
            )
        }

        vb.btnRelax.setOnClickListener {
            if (!entitlements.adsRemoved) ads.showInterstitialIfReady()
            toast("Relax mode on. Press & hold the slime.")
        }

        maybeShowRemoveAdsReminder()
        updateTopUI()
    }

    override fun onDestroy() {
        billing.end()
        soundscape.stop()
        soundPool?.release()
        soundPool = null
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_SHOP || resultCode != RESULT_OK || data == null) return

        data.getStringExtra(ShopActivity.RESULT_BUY_PRODUCT)?.let { productId ->
            if (!hasNetwork()) {
                toast("Offline: purchases unavailable")
                return
            }
            billing.launchPurchase(this, productId)
            return
        }

        data.getStringExtra(ShopActivity.RESULT_EQUIP_SKIN)?.let { id ->
            if (!entitlements.ownsSkin(id)) { toast("Not owned"); return }
            equippedSkinId = id
            Prefs.setEquippedSkinId(this, equippedSkinId)
            applyEquippedSkinToView()
            updateTopUI()
            return
        }

        data.getStringExtra(ShopActivity.RESULT_EQUIP_SOUND)?.let { id ->
            if (!entitlements.ownsSound(id)) { toast("Not owned"); return }
            equippedSoundId = id
            Prefs.setEquippedSoundId(this, equippedSoundId)
            applySoundscape()
            updateTopUI()
            return
        }
    }

    private fun updateTopUI() {
        vb.tvCoins.text = "Coins: $coins"

        val skinName = ContentNames.skinNameFor(equippedSkinId)
        val soundName = ContentNames.soundNameFor(equippedSoundId)

        val dailyReady = Prefs.getDailyClaimDate(this) != LocalDate.now().toString()
        val q = questState ?: QuestManager.loadOrInit(this)
        val quests = QuestManager.todaysQuests()
        val completed = quests.count { QuestManager.canClaim(it, q) }
        vb.tvEquipped.text = "Skin: $skinName | Sound: $soundName"
        vb.tvDaily.text = "Daily: ${if (dailyReady) "ready" else "claimed"} · Quests: $completed ready"

        if (entitlements.adsRemoved) {
            vb.btnRewardedBoost.isEnabled = false
            vb.btnRewardedBoost.text = "Ad-Free ✅"
        } else {
            vb.btnRewardedBoost.isEnabled = true
            vb.btnRewardedBoost.text = "Watch Ad: 2× Coins (60s)"
        }
    }

    private fun applyEquippedSkinToView() {
        val idx = equippedSkinId.removePrefix("skin_").toIntOrNull()?.coerceIn(1, 50) ?: 1
        vb.slimeView.setSkinIndex(idx - 1)
    }

    private fun applySoundscape() {
        if (!soundEnabled) {
            soundscape.stop()
            return
        }
        val resId = SoundLibrary.soundpackResId(this, equippedSoundId)
        if (resId == 0) {
            soundscape.stop()
            return
        }
        soundscape.playLoop(resId)
    }

    private fun applyEntitlements() {
        if (!entitlements.ownsSkin(equippedSkinId)) {
            equippedSkinId = "skin_001"
            Prefs.setEquippedSkinId(this, equippedSkinId)
            applyEquippedSkinToView()
        }
        if (!entitlements.ownsSound(equippedSoundId)) {
            equippedSoundId = "sound_001"
            Prefs.setEquippedSoundId(this, equippedSoundId)
            applySoundscape()
        }

        ads.adsEnabled = !entitlements.adsRemoved

        updateTopUI()
    }

    private fun showDailyAndQuestsDialog() {
        val todayIso = LocalDate.now().toString()
        val lastClaim = Prefs.getDailyClaimDate(this)
        val canClaim = lastClaim != todayIso

        val streak = Prefs.getDailyStreak(this)
        val base = 120
        val bonusDays = streak.coerceIn(0, 7)
        val bonus = bonusDays * 25
        val total = base + bonus

        val qState = QuestManager.loadOrInit(this)
        questState = qState
        val quests = QuestManager.todaysQuests()

        val questLines = quests.joinToString("\n") { q ->
            val prog = qState.progress[q.idx]
            val status = when {
                qState.claimed[q.idx] -> "claimed ✅"
                prog >= q.target -> "ready 🎁"
                else -> "${prog}/${q.target}"
            }
            "• ${q.title} — $status (+${q.rewardCoins})"
        }

        val msg = buildString {
            append("Daily reward: $base\n")
            append("Streak bonus: +$bonus (streak: $streak)\n\n")
            append("Quests:\n$questLines\n\n")
            if (canClaim) append("Daily is ready to claim.") else append("Daily already claimed today.")
        }

        AlertDialog.Builder(this)
            .setTitle("Daily + Quests")
            .setMessage(msg)
            .setPositiveButton(if (canClaim) "Claim Daily" else "Close") { _, _ ->
                if (canClaim) claimDaily(todayIso, total)
            }
            .setNeutralButton(if (!entitlements.adsRemoved && canClaim) "Claim + Double (Ad)" else "OK") { _, _ ->
                if (!entitlements.adsRemoved && canClaim) {
                    ads.showRewarded(
                        onReward = { claimDaily(todayIso, total * 2) },
                        onNotReady = { toast("Ad not ready (or offline)") }
                    )
                }
            }
            .setNegativeButton("Claim Quests") { _, _ ->
                claimReadyQuests()
            }
            .show()
    }

    private fun claimDaily(todayIso: String, amount: Int) {
        val last = Prefs.getDailyClaimDate(this)
        val yesterday = LocalDate.now().minusDays(1).toString()
        val newStreak = if (last == yesterday) Prefs.getDailyStreak(this) + 1 else 1

        Prefs.setDailyClaimDate(this, todayIso)
        Prefs.setDailyStreak(this, newStreak)

        coins += amount
        Prefs.setCoins(this, coins)

        updateTopUI()
        toast("Claimed $amount (streak: $newStreak)")
    }

    private fun claimReadyQuests() {
        var st = questState ?: QuestManager.loadOrInit(this)
        var claimedAny = false
        QuestManager.todaysQuests().forEach { q ->
            if (QuestManager.canClaim(q, st)) {
                st = QuestManager.claim(this, q, st)
                coins += q.rewardCoins
                claimedAny = true
            }
        }
        Prefs.setCoins(this, coins)
        questState = st
        updateTopUI()
        toast(if (claimedAny) "Quest rewards claimed" else "No quests ready yet")
    }

    private fun maybeShowRemoveAdsReminder() {
        if (entitlements.adsRemoved) return

        val launches = Prefs.getLaunchCount(this) + 1
        Prefs.setLaunchCount(this, launches)

        val today = LocalDate.now()
        val lastIso = Prefs.getUpsellLastIso(this)
        val last = lastIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        val showByLaunch = launches == 2 || launches == 5 || launches == 10
        val showByWeek = last == null || ChronoUnit.DAYS.between(last, today) >= 7

        if (!(showByLaunch || showByWeek)) return

        Prefs.setUpsellLastIso(this, today.toString())
        AlertDialog.Builder(this)
            .setTitle("Go Ad-Free")
            .setMessage("One-time $4.99 purchase removes all ads forever.")
            .setPositiveButton("Buy") { _, _ ->
                if (!hasNetwork()) toast("Offline: purchases unavailable")
                else billing.launchPurchase(this, Catalog.REMOVE_ADS)
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun hasNetwork(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun setupPopSound() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()

        popSoundId = try {
            val id = resources.getIdentifier("pop", "raw", packageName)
            if (id != 0) soundPool!!.load(this, id, 1) else 0
        } catch (_: Exception) { 0 }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

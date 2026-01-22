package com.slimepop.asmr

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.games.PlayGames
import com.slimepop.asmr.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

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

    private val shopLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult

            data.getStringExtra(ShopActivity.RESULT_BUY_PRODUCT)?.let { productId ->
                if (!hasNetwork()) {
                    toast("Offline: purchases unavailable")
                    return@registerForActivityResult
                }
                billing.launchPurchase(this, productId)
            }

            data.getStringExtra(ShopActivity.RESULT_EQUIP_SKIN)?.let { id ->
                if (!entitlements.ownsSkin(id)) { toast("Not owned"); return@registerForActivityResult }
                equippedSkinId = id
                Prefs.setEquippedSkinId(this, equippedSkinId)
                applyEquippedSkinToView()
                updateTopUI()
                CloudSaveManager.saveToCloud(this)
            }

            data.getStringExtra(ShopActivity.RESULT_EQUIP_SOUND)?.let { id ->
                if (!entitlements.ownsSound(id)) { toast("Not owned"); return@registerForActivityResult }
                equippedSoundId = id
                Prefs.setEquippedSoundId(this, equippedSoundId)
                applySoundscape()
                updateTopUI()
                CloudSaveManager.saveToCloud(this)
            }
        }
    }
    private val boostDurationMs = 60_000L

    private fun getTodayIso(): String {
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        } catch (e: Exception) {
            "2024-01-01"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d("SlimePop", "MainActivity onCreate started")

        try {
            vb = ActivityMainBinding.inflate(layoutInflater)
            setContentView(vb.root)
            vb.root.setBackgroundColor(Color.parseColor("#1C2632"))
            vb.slimeView.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e("SlimePop", "Inflation error", e)
            return
        }

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
        billing = BillingManager(this) { ent ->
            entitlements = ent
            applyEntitlements()
            CloudSaveManager.saveToCloud(this)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                Log.d("SlimePop", "Initializing SDKs...")
                ads.adsEnabled = !entitlements.adsRemoved
                ads.init()
                billing.start()
                checkCloudSave()
            } catch (e: Exception) {
                Log.e("SlimePop", "SDK Init error", e)
            }
        }, 1000)

        vb.btnToggleSound.text = if (soundEnabled) "Sound: ON" else "Sound: OFF"

        applyEntitlements()
        applyEquippedSkinToView()

        vb.slimeView.onPop = { baseCoins, holdMs ->
            val multiplier = if (!entitlements.adsRemoved && SystemClock.elapsedRealtime() < boostUntilMs) 2 else 1
            val earned = baseCoins * multiplier

            coins += earned
            Prefs.setCoins(this, coins)

            Prefs.setTotalPops(this, Prefs.getTotalPops(this) + 1)
            Prefs.setTotalHoldMs(this, Prefs.getTotalHoldMs(this) + holdMs)

            // Optimized Quest update (one save instead of two)
            questState = QuestManager.updateProgress(this, questState ?: QuestManager.loadOrInit(this), 1, holdMs)

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
            
            // Periodically save to cloud
            if (Prefs.getTotalPops(this) % 50 == 0) {
                CloudSaveManager.saveToCloud(this)
            }
        }

        vb.btnToggleSound.setOnClickListener {
            soundEnabled = !soundEnabled
            Prefs.setSound(this, soundEnabled)
            vb.btnToggleSound.text = if (soundEnabled) "Sound: ON" else "Sound: OFF"
            applySoundscape()
        }

        vb.btnDaily.setOnClickListener { showDailyAndQuestsDialog() }

        vb.btnShop.setOnClickListener {
            val i = Intent(this, ShopActivity::class.java)
                .putExtra(ShopActivity.EXTRA_OWNED_PRODUCTS_CSV, Prefs.getOwnedIapCsv(this))
                .putExtra(ShopActivity.EXTRA_EQUIPPED_SKIN, equippedSkinId)
                .putExtra(ShopActivity.EXTRA_EQUIPPED_SOUND, equippedSoundId)

            shopLauncher.launch(i)
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
            applySoundscape() 
        }

        vb.tvPrivacy.setOnClickListener {
            startActivity(Intent(this, PrivacyActivity::class.java))
        }

        maybeShowRemoveAdsReminder()
        updateTopUI()
        
        Log.d("SlimePop", "MainActivity onCreate finished")
    }

    private fun checkCloudSave() {
        PlayGames.getGamesSignInClient(this).isAuthenticated.addOnSuccessListener { result ->
            if (result.isAuthenticated) {
                CloudSaveManager.loadFromCloud(this) {
                    runOnUiThread {
                        coins = Prefs.getCoins(this)
                        equippedSkinId = Prefs.getEquippedSkinId(this)
                        equippedSoundId = Prefs.getEquippedSoundId(this)
                        
                        val ownedCsv = Prefs.getOwnedIapCsv(this)
                        val ownedSet = EntitlementResolver.ownedSetFromCsv(ownedCsv)
                        entitlements = EntitlementResolver.resolveFromOwnedProducts(ownedSet)
                        
                        applyEntitlements()
                        applyEquippedSkinToView()
                        applySoundscape()
                        updateTopUI()
                        toast("Progress restored from cloud")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        try {
            CloudSaveManager.saveToCloud(this)
            billing.end()
            soundscape.stop()
            soundPool?.release()
        } catch (_: Exception) {}
        soundPool = null
        super.onDestroy()
    }

    private fun updateTopUI() {
        vb.tvCoins.text = "Coins: $coins"
        vb.tvCoins.setTextColor(Color.WHITE)

        val skinName = ContentNames.skinNameFor(equippedSkinId)
        val soundName = ContentNames.soundNameFor(equippedSoundId)

        val dailyReady = Prefs.getDailyClaimDate(this) != getTodayIso()
        val q = questState ?: QuestManager.loadOrInit(this)
        val quests = QuestManager.todaysQuests()
        val completed = quests.count { QuestManager.canClaim(it, q) }
        vb.tvEquipped.text = "Skin: $skinName | Sound: $soundName"
        vb.tvEquipped.setTextColor(Color.parseColor("#BBBBBB"))
        
        vb.tvDaily.text = "Daily: ${if (dailyReady) "ready" else "claimed"} · Quests: $completed ready"
        vb.tvDaily.setTextColor(Color.parseColor("#BBBBBB"))

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
        val todayIso = getTodayIso()
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
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = try { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time) } catch(e:Exception) { "" }
        
        val newStreak = if (last == yesterday) Prefs.getDailyStreak(this) + 1 else 1

        Prefs.setDailyClaimDate(this, todayIso)
        Prefs.setDailyStreak(this, newStreak)

        coins += amount
        Prefs.setCoins(this, coins)

        updateTopUI()
        toast("Claimed $amount (streak: $newStreak)")
        CloudSaveManager.saveToCloud(this)
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
        if (claimedAny) CloudSaveManager.saveToCloud(this)
    }

    private fun maybeShowRemoveAdsReminder() {
        if (entitlements.adsRemoved) return

        val launches = Prefs.getLaunchCount(this) + 1
        Prefs.setLaunchCount(this, launches)

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = Date()
        val lastIso = Prefs.getUpsellLastIso(this)
        val lastDate = lastIso?.let { try { sdf.parse(it) } catch(e: Exception) { null } }

        val showByLaunch = launches == 2 || launches == 5 || launches == 10
        
        var showByWeek = false
        if (lastDate == null) {
            showByWeek = true
        } else {
            val diff = today.time - lastDate.time
            val days = diff / (1000 * 60 * 60 * 24)
            if (days >= 7) showByWeek = true
        }

        if (!(showByLaunch || showByWeek)) return

        Prefs.setUpsellLastIso(this, try { sdf.format(today) } catch(e:Exception) { "" })
        
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                AlertDialog.Builder(this)
                    .setTitle("Go Ad-Free")
                    .setMessage("One-time $4.99 purchase removes all ads forever.")
                    .setCancelable(true)
                    .setPositiveButton("Buy") { _, _ ->
                        if (!hasNetwork()) toast("Offline: purchases unavailable")
                        else billing.launchPurchase(this, Catalog.REMOVE_ADS)
                    }
                    .setNegativeButton("Not now") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }, 3000)
    }

    private fun hasNetwork(): Boolean {
        return try {
            val cm = getSystemService(ConnectivityManager::class.java)
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
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

        // Using direct R reference is better than getIdentifier
        popSoundId = try {
            val id = R.raw.pop
            if (id != 0) soundPool!!.load(this, id, 1) else 0
        } catch (_: Exception) {
            // Fallback just in case
            try {
                val id = resources.getIdentifier("pop", "raw", packageName)
                if (id != 0) soundPool!!.load(this, id, 1) else 0
            } catch (_: Exception) { 0 }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
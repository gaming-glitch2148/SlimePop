package com.slimepop.asmr

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.slimepop.asmr.audio.SlimeAudioManager
import com.slimepop.asmr.databinding.ActivityMainBinding
import java.time.LocalDate
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var vb: ActivityMainBinding
    private var coins = 0
    private var equippedSkinId = "skin_ocean"
    private var equippedSoundId = "sound_001"
    private var isRelaxMode = false

    private lateinit var billing: BillingManager
    private lateinit var audio: SlimeAudioManager
    private lateinit var adManager: AdManager
    private var entitlements = EntitlementResolver.resolveFromOwnedProducts(emptySet())
    private var boostTicker: Runnable? = null
    private val bubblePopResIds = intArrayOf(
        R.raw.bubble_pop_01,
        R.raw.bubble_pop_02,
        R.raw.bubble_pop_03,
        R.raw.bubble_pop_04,
        R.raw.bubble_pop_05,
        R.raw.bubble_pop_06
    )
    private val sfxRandom = Random(System.currentTimeMillis())

    private val boostMultiplier = 3
    private val boostDurationMs = 3 * 60 * 1000L
    private val boostMaxRemainingMs = 15 * 60 * 1000L
    private val dailyAdBonusRatio = 0.60f

    private val shopLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult

            data.getStringExtra(ShopActivity.RESULT_EQUIP_SKIN)?.let { id ->
                equippedSkinId = id
                Prefs.setEquippedSkinId(this, id)
                vb.slimeView.setSkin(id)
                updateTopUI()
            }

            data.getStringExtra(ShopActivity.RESULT_EQUIP_SOUND)?.let { id ->
                equippedSoundId = id
                Prefs.setEquippedSoundId(this, id)
                updateSound()
                updateTopUI()
            }

            data.getStringExtra(ShopActivity.RESULT_BUY_PRODUCT)?.let { productId ->
                if (Monetization.requiresPlayPurchase(productId)) {
                    billing.launchPurchase(this, productId)
                    return@let
                }

                val coinCost = Monetization.coinPriceFor(productId) ?: 0
                if (coinCost > 0 && coins < coinCost) {
                    Toast.makeText(this, "Need ${coinCost - coins} more coins!", Toast.LENGTH_SHORT).show()
                    return@let
                }

                if (coinCost > 0) {
                    coins -= coinCost
                    Prefs.setCoins(this, coins)
                }

                addOwnedProduct(productId)
                if (coinCost > 0) {
                    RevenueTelemetry.trackPurchaseConfirmed(
                        this,
                        productId,
                        RevenueTelemetry.PurchaseSource.COINS
                    )
                }
                refreshEntitlements()
                updateTopUI()
                Toast.makeText(this, "Unlocked ${displayNameFor(productId)}!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vb.root)

        audio = SlimeAudioManager(this)
        adManager = AdManager(this, this)

        billing = BillingManager(this) { newEnt ->
            entitlements = newEnt
            refreshEntitlements()
            adManager.adsEnabled = !entitlements.adsRemoved
        }
        billing.start()

        coins = Prefs.getCoins(this)
        equippedSkinId = Prefs.getEquippedSkinId(this)
        equippedSoundId = Prefs.getEquippedSoundId(this)
        entitlements = EntitlementResolver.resolveFromOwnedProducts(
            EntitlementResolver.ownedSetFromCsv(Prefs.getOwnedIapCsv(this))
        )

        adManager.adsEnabled = !entitlements.adsRemoved
        adManager.init()

        vb.slimeView.setSkin(equippedSkinId)
        updateTopUI()
        updateDailyUi()
        updateBoostButtonState()

        bubblePopResIds.forEach { audio.preload(it) }
        vb.root.postDelayed({
            updateSound()
        }, 500)

        vb.btnShop.setOnClickListener {
            val intent = Intent(this, ShopActivity::class.java).apply {
                putExtra(ShopActivity.EXTRA_EQUIPPED_SKIN, equippedSkinId)
                putExtra(ShopActivity.EXTRA_EQUIPPED_SOUND, equippedSoundId)
                putExtra(ShopActivity.EXTRA_USER_COINS, coins)
                putExtra(ShopActivity.EXTRA_OWNED_PRODUCTS_CSV, Prefs.getOwnedIapCsv(this@MainActivity))
            }
            shopLauncher.launch(intent)
        }

        vb.btnToggleSound.setOnClickListener {
            val current = Prefs.getSound(this)
            Prefs.setSound(this, !current)
            updateSound()
        }

        vb.btnDaily.setOnClickListener {
            when {
                !isDailyClaimedToday() -> claimDailyReward()
                !hasDailyAdBonusToday() -> claimDailyAdBonus()
                else -> Toast.makeText(this, "Daily reward already completed.", Toast.LENGTH_SHORT).show()
            }
        }

        vb.btnRelax.setOnClickListener {
            if (!isRelaxMode) {
                showRelaxModeInfo()
            } else {
                toggleRelaxMode(false)
            }
        }

        vb.btnRewardedBoost.setOnClickListener {
            if (isRelaxMode) return@setOnClickListener
            adManager.showRewarded(
                onReward = {
                    val now = System.currentTimeMillis()
                    val currentUntil = Prefs.getBoostUntilMs(this)
                    val baseUntil = maxOf(now, currentUntil)
                    val maxAllowedUntil = now + boostMaxRemainingMs
                    val until = (baseUntil + boostDurationMs).coerceAtMost(maxAllowedUntil)
                    Prefs.setBoostUntilMs(this, until)
                    updateBoostButtonState()
                    val minutes = ((until - now) / 60_000L).coerceAtLeast(1L)
                    Toast.makeText(this, "x$boostMultiplier coin boost active ($minutes min).", Toast.LENGTH_SHORT).show()
                },
                onNotReady = {
                    Toast.makeText(this, "Rewarded ad not ready yet. Try again in a moment.", Toast.LENGTH_SHORT).show()
                }
            )
        }

        vb.slimeView.onPop = { earned, holdMs ->
            if (!isRelaxMode && earned > 0) {
                val payout = earned * currentCoinMultiplier()
                coins += payout
                Prefs.setCoins(this, coins)
                updateTopUI()
            }

            val totalPops = Prefs.getTotalPops(this) + 1
            Prefs.setTotalPops(this, totalPops)
            Prefs.setTotalHoldMs(this, Prefs.getTotalHoldMs(this) + holdMs)

            playBubblePopSfx(holdMs)
        }
    }

    private fun showRelaxModeInfo() {
        AlertDialog.Builder(this)
            .setTitle("Relax Mode")
            .setMessage("In Relax Mode, coin collection and daily rewards are disabled so you can focus purely on the ASMR experience. \n\nSwitch to this mode?")
            .setPositiveButton("Enter Relax Mode") { _, _ -> toggleRelaxMode(true) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleRelaxMode(enable: Boolean) {
        isRelaxMode = enable
        vb.slimeView.isRelaxMode = enable

        val visibility = if (enable) View.GONE else View.VISIBLE
        vb.tvCoins.visibility = visibility
        vb.tvDaily.visibility = visibility
        vb.btnDaily.visibility = visibility
        vb.btnRewardedBoost.visibility = visibility

        vb.btnRelax.text = if (enable) "Normal Mode" else "Relax Mode"

        val msg = if (enable) "Relax Mode Active" else "Back to Normal Mode"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        updateDailyUi()
        updateBoostButtonState()
    }

    private fun updateSound() {
        val enabled = Prefs.getSound(this)
        vb.btnToggleSound.text = if (enabled) "Sound: ON" else "Sound: OFF"
        if (enabled) {
            val resId = SoundLibrary.soundpackResId(this, equippedSoundId)
            if (resId != 0) {
                audio.preload(resId)
                vb.root.postDelayed({
                    audio.playLoop("ambience", resId, volume = 0.4f)
                }, 200)
            }
        } else {
            audio.stopLoop("ambience")
        }
    }

    private fun refreshEntitlements() {
        val csv = Prefs.getOwnedIapCsv(this)
        entitlements = EntitlementResolver.resolveFromOwnedProducts(EntitlementResolver.ownedSetFromCsv(csv))
    }

    private fun addOwnedProduct(productId: String) {
        val owned = EntitlementResolver.ownedSetFromCsv(Prefs.getOwnedIapCsv(this)).toMutableSet()
        if (owned.add(productId)) {
            Prefs.setOwnedIapCsv(this, owned.sorted().joinToString(","))
        }
    }

    private fun displayNameFor(productId: String): String {
        val skin = SkinCatalog.skins.find { it.id == productId }
        if (skin != null) return skin.name
        val sound = SoundCatalog.sounds.find { it.id == productId }
        if (sound != null) return sound.name
        return productId
    }

    private fun playBubblePopSfx(holdMs: Long) {
        if (!Prefs.getSound(this) || bubblePopResIds.isEmpty()) return

        val intensity = (holdMs / 220f).coerceIn(0f, 1f)
        val popResId = bubblePopResIds[sfxRandom.nextInt(bubblePopResIds.size)]
        val baseRate = (0.96f + intensity * 0.12f + sfxRandom.nextFloat() * 0.06f).coerceIn(0.88f, 1.26f)
        val baseVolume = (0.52f + intensity * 0.30f + sfxRandom.nextFloat() * 0.07f).coerceIn(0.35f, 1f)
        audio.playSfx(popResId, volume = baseVolume, rate = baseRate)

        val layerChance = 0.30f + intensity * 0.35f
        if (sfxRandom.nextFloat() < layerChance) {
            val layerResId = bubblePopResIds[sfxRandom.nextInt(bubblePopResIds.size)]
            val layerDelayMs = 7L + sfxRandom.nextInt(10).toLong()
            val layerRate = (baseRate + 0.10f + sfxRandom.nextFloat() * 0.06f).coerceIn(0.92f, 1.38f)
            val layerVolume = (baseVolume * 0.36f).coerceAtLeast(0.16f)
            vb.root.postDelayed({
                audio.playSfx(layerResId, volume = layerVolume, rate = layerRate)
            }, layerDelayMs)
        }
    }

    private fun currentCoinMultiplier(): Int {
        if (isRelaxMode) return 1
        return if (System.currentTimeMillis() < Prefs.getBoostUntilMs(this)) boostMultiplier else 1
    }

    private fun updateTopUI() {
        val mult = currentCoinMultiplier()
        val multTag = if (mult > 1) " (x$mult)" else ""
        vb.tvCoins.text = "Coins: $coins$multTag"
        vb.tvEquipped.text = "Skin: $equippedSkinId | Sound: $equippedSoundId"
    }

    private fun updateDailyUi() {
        val today = LocalDate.now().toString()
        val claimedToday = Prefs.getDailyClaimDate(this) == today
        val adBonusToday = Prefs.getDailyAdBonusDate(this) == today
        val streak = Prefs.getDailyStreak(this)

        vb.tvDaily.text = if (!claimedToday) {
            "Daily ready | Streak: $streak"
        } else if (!adBonusToday) {
            "Daily claimed | Bonus ad available | Streak: $streak"
        } else {
            "Daily claimed | Bonus used | Streak: $streak"
        }

        vb.btnDaily.text = when {
            !claimedToday -> "Claim Daily"
            !adBonusToday -> "Daily Bonus Ad (+60%)"
            else -> "Daily Claimed"
        }
        vb.btnDaily.isEnabled = !isRelaxMode && (!claimedToday || !adBonusToday)
    }

    private fun claimDailyReward() {
        if (isRelaxMode) return

        val today = LocalDate.now()
        val todayIso = today.toString()
        val lastIso = Prefs.getDailyClaimDate(this)
        if (lastIso == todayIso) {
            Toast.makeText(this, "Daily reward already claimed.", Toast.LENGTH_SHORT).show()
            return
        }

        val lastDate = runCatching { lastIso?.let(LocalDate::parse) }.getOrNull()
        val prevStreak = Prefs.getDailyStreak(this)
        val streak = if (lastDate != null && lastDate.plusDays(1) == today) prevStreak + 1 else 1
        val baseReward = rewardForStreak(streak)
        val payout = baseReward * currentCoinMultiplier()

        coins += payout
        Prefs.setCoins(this, coins)
        Prefs.setDailyClaimDate(this, todayIso)
        Prefs.setDailyStreak(this, streak)
        Prefs.setDailyLastReward(this, baseReward)
        Prefs.setDailyAdBonusDate(this, null)

        updateDailyUi()
        updateTopUI()
        Toast.makeText(this, "Daily +$payout coins (streak $streak)", Toast.LENGTH_SHORT).show()
        ReviewHelper.maybeAskForReview(this, streak, Prefs.getTotalPops(this))
    }

    private fun claimDailyAdBonus() {
        if (isRelaxMode) return
        val todayIso = LocalDate.now().toString()
        if (!isDailyClaimedToday()) {
            claimDailyReward()
            return
        }
        if (hasDailyAdBonusToday()) {
            Toast.makeText(this, "Daily bonus already claimed.", Toast.LENGTH_SHORT).show()
            return
        }

        adManager.showRewarded(
            onReward = {
                val base = Prefs.getDailyLastReward(this).coerceAtLeast(200)
                val bonusBase = (base * dailyAdBonusRatio).toInt().coerceAtLeast(150)
                val payout = bonusBase * currentCoinMultiplier()
                coins += payout
                Prefs.setCoins(this, coins)
                Prefs.setDailyAdBonusDate(this, todayIso)
                updateDailyUi()
                updateTopUI()
                Toast.makeText(this, "Daily bonus +$payout coins", Toast.LENGTH_SHORT).show()
            },
            onNotReady = {
                Toast.makeText(this, "Rewarded ad not ready yet. Try again soon.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun rewardForStreak(streak: Int): Int {
        val day = streak.coerceAtLeast(1)
        return when {
            day == 1 -> 220
            day == 2 -> 280
            day == 3 -> 360
            day == 4 -> 460
            day == 5 -> 580
            day == 6 -> 720
            else -> 900
        }
    }

    private fun isDailyClaimedToday(): Boolean = Prefs.getDailyClaimDate(this) == LocalDate.now().toString()

    private fun hasDailyAdBonusToday(): Boolean = Prefs.getDailyAdBonusDate(this) == LocalDate.now().toString()

    private fun updateBoostButtonState() {
        if (isRelaxMode) {
            vb.btnRewardedBoost.text = "Boost unavailable in Relax mode"
            vb.btnRewardedBoost.isEnabled = false
            updateTopUI()
            return
        }
        val remainingMs = Prefs.getBoostUntilMs(this) - System.currentTimeMillis()
        if (remainingMs > 0) {
            val remainingSec = (remainingMs / 1000L).coerceAtLeast(0L)
            vb.btnRewardedBoost.text = "Boost x$boostMultiplier (${remainingSec}s) • Extend +3m"
            vb.btnRewardedBoost.isEnabled = true
        } else {
            vb.btnRewardedBoost.text = "Watch Ad: x$boostMultiplier Coins (3m)"
            vb.btnRewardedBoost.isEnabled = true
        }
        updateTopUI()
    }

    private fun startBoostTicker() {
        stopBoostTicker()
        val runner = object : Runnable {
            override fun run() {
                updateBoostButtonState()
                vb.root.postDelayed(this, 1000L)
            }
        }
        boostTicker = runner
        vb.root.post(runner)
    }

    private fun stopBoostTicker() {
        boostTicker?.let { vb.root.removeCallbacks(it) }
        boostTicker = null
    }

    override fun onResume() {
        super.onResume()
        audio.resumeAll()
        updateDailyUi()
        startBoostTicker()
    }

    override fun onPause() {
        stopBoostTicker()
        audio.pauseAll()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBoostTicker()
        audio.release()
        billing.end()
    }
}

package com.slimepop.asmr

import android.app.Activity
import android.content.Intent
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import com.slimepop.asmr.audio.SlimeAudioManager
import com.slimepop.asmr.databinding.ActivityMainBinding
import java.time.LocalDate
import java.util.Locale
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var vb: ActivityMainBinding
    private var coins = 0
    private var equippedSkinId = "skin_ocean"
    private var equippedSoundId = "sound_001"
    private var isRelaxMode = false
    private var isChildUser = true

    private lateinit var billing: BillingManager
    private lateinit var audio: SlimeAudioManager
    private lateinit var adManager: AdManager
    private var entitlements = EntitlementResolver.resolveFromOwnedProducts(emptySet())
    private var boostTicker: Runnable? = null
    private var premiumPulseTicker: Runnable? = null
    private var lastPremiumNudgeMs = 0L
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
                    refreshEntitlements()
                    adManager.adsEnabled = !entitlements.adsRemoved
                    updateTopUI()
                    return@let
                }

                val coinCost = Monetization.coinPriceFor(productId) ?: 0
                if (coinCost > 0 && coins < coinCost) {
                    Stripe.show(this, "Need ${coinCost - coins} more coins!")
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
                Stripe.show(this, "Unlocked ${displayNameFor(productId)}!")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vb.root)
        applySafeAreaInsets()

        audio = SlimeAudioManager(this)
        adManager = AdManager(this, this) {
            runOnUiThread { maybeShowPostAdPremiumUpsell() }
        }

        billing = BillingManager(
            context = this,
            onEntitlements = { newEnt ->
                runOnUiThread {
                    entitlements = newEnt
                    refreshEntitlements()
                    adManager.adsEnabled = !entitlements.adsRemoved
                    adManager.rewardedEnabled = !isChildUser && !entitlements.adsRemoved
                    updateTopUI()
                }
            },
            onCatalogUpdated = {
                runOnUiThread {
                    updateTopUI()
                }
            }
        )
        billing.start()

        coins = Prefs.getCoins(this)
        equippedSkinId = Prefs.getEquippedSkinId(this)
        equippedSoundId = Prefs.getEquippedSoundId(this)
        entitlements = EntitlementResolver.resolveFromOwnedProducts(
            EntitlementResolver.ownedSetFromCsv(Prefs.getOwnedIapCsv(this))
        )

        isChildUser = Prefs.isChildUser(this)
        adManager.setChildDirected(isChildUser)
        adManager.adsEnabled = !entitlements.adsRemoved
        adManager.rewardedEnabled = !isChildUser && !entitlements.adsRemoved
        adManager.init()

        vb.slimeView.setSkin(equippedSkinId)
        syncHapticsPreference()
        updateTopUI()
        updateDailyUi()
        updateBoostButtonState()
        ensureAgeGate()

        bubblePopResIds.forEach { audio.preload(it) }
        vb.root.postDelayed({
            updateSound()
        }, 500)

        vb.btnShop.setOnClickListener {
            openShop(initialTab = 1)
        }

        vb.btnSkins.setOnClickListener {
            openShop(initialTab = 0)
        }

        vb.btnSettings.setOnClickListener {
            showSettingsPanel()
        }

        vb.btnRemoveAds.setOnClickListener {
            launchPremiumCheckout("top_chip")
        }

        vb.btnPremiumBanner.setOnClickListener {
            launchPremiumCheckout("main_banner")
        }

        vb.btnToggleSound.setOnClickListener {
            val current = Prefs.getSound(this)
            Prefs.setSound(this, !current)
            updateSound()
        }

        vb.btnDaily.setOnClickListener {
            when {
                !isDailyClaimedToday() -> claimDailyReward()
                !hasDailyAdBonusToday() && !isChildUser -> claimDailyAdBonus()
                !hasDailyAdBonusToday() && isChildUser ->
                    Stripe.show(this, "Bonus not available for younger users.")
                else -> Stripe.show(this, "Daily resets in ${timeUntilNextDailyReset()}.")
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
            if (isChildUser) {
                Stripe.show(this, "Not available for younger users.")
                return@setOnClickListener
            }
            promptRewardedAd(
                title = "Watch Ad",
                message = "Watch a short ad to activate a x$boostMultiplier coin boost for 3 minutes."
            ) {
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
                        Stripe.show(this, "x$boostMultiplier coin boost active ($minutes min).")
                    },
                    onNotReady = {
                        Stripe.show(this, "Rewarded ad not ready yet. Try again in a moment.")
                    }
                )
            }
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
            if (!isRelaxMode && !entitlements.adsRemoved) {
                adManager.incrementPopAndMaybeShowInterstitial()
            }
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

    private fun ensureAgeGate() {
        if (Prefs.getAgeBucket(this) != null) return
        showAgeGateDialog()
    }

    private fun showAgeGateDialog() {
        val options = arrayOf("Under 13", "13-15", "16-17", "18+")
        AlertDialog.Builder(this)
            .setTitle("Age Check")
            .setMessage("Select your age group. This helps us show appropriate ads.")
            .setCancelable(false)
            .setItems(options) { _, which ->
                val bucket = when (which) {
                    0 -> "u13"
                    1 -> "13_15"
                    2 -> "16_17"
                    else -> "18_plus"
                }
                Prefs.setAgeBucket(this, bucket)
                applyAgeBucket()
            }
            .show()
    }

    private fun applyAgeBucket() {
        isChildUser = Prefs.isChildUser(this)
        adManager.setChildDirected(isChildUser)
        adManager.rewardedEnabled = !isChildUser && !entitlements.adsRemoved
        updateDailyUi()
        updateBoostButtonState()
        updateTopUI()
    }

    private fun promptRewardedAd(title: String, message: String, onAccept: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Watch Ad") { _, _ -> onAccept() }
            .setNegativeButton("No Thanks", null)
            .show()
    }

    private fun applySafeAreaInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(vb.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(vb.root)
    }

    private fun launchPremiumCheckout(source: String) {
        if (entitlements.adsRemoved) {
            Stripe.show(this, "Premium Pass is already active.")
            return
        }
        val launched = billing.launchPurchase(this, Catalog.REMOVE_ADS)
        if (!launched) {
            val reason = billing.checkoutBlockingReason(Catalog.REMOVE_ADS)
                ?: "Google Play is connecting. Try again in a moment."
            Stripe.show(this, reason)
            return
        }
        RevenueTelemetry.trackBuyClick(this, Catalog.REMOVE_ADS, source)
    }

    private fun maybeShowPostAdPremiumUpsell() {
        if (entitlements.adsRemoved || isFinishing || isDestroyed) return
        val now = System.currentTimeMillis()
        if (now - lastPremiumNudgeMs < 90_000L) return
        lastPremiumNudgeMs = now
        Snackbar.make(
            vb.root,
            "Tired of ads? Go Premium for ad-free + permanent 2X coins.",
            Snackbar.LENGTH_LONG
        ).setAction("GO VIP") {
            launchPremiumCheckout("post_ad_prompt")
        }.show()
    }

    private fun showSettingsPanel() {
        val items = mutableListOf<Pair<String, () -> Unit>>()
        items += (if (Prefs.getSound(this)) "Sound: ON (tap to mute)" else "Sound: OFF (tap to enable)") to {
            Prefs.setSound(this, !Prefs.getSound(this))
            updateSound()
        }
        items += (if (Prefs.getHaptics(this)) "Haptics: ON" else "Haptics: OFF") to {
            val enabled = !Prefs.getHaptics(this)
            setHapticsPreference(enabled)
            Stripe.show(this, if (enabled) "Haptics enabled" else "Haptics disabled")
        }
        items += "Privacy Policy" to {
            startActivity(Intent(this, PrivacyActivity::class.java))
        }
        if (!entitlements.adsRemoved) {
            items += "Premium Pass (No Ads + 2X Coins)" to { launchPremiumCheckout("settings_panel") }
        }

        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setItems(items.map { it.first }.toTypedArray()) { _, which ->
                items[which].second.invoke()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun setHapticsPreference(enabled: Boolean) {
        Prefs.setHaptics(this, enabled)
        vb.slimeView.hapticsEnabled = enabled
        vb.slimeView.isHapticFeedbackEnabled = enabled
    }

    private fun syncHapticsPreference() {
        setHapticsPreference(Prefs.getHaptics(this))
    }

    private fun openShop(initialTab: Int) {
        val intent = Intent(this, ShopActivity::class.java).apply {
            putExtra(ShopActivity.EXTRA_EQUIPPED_SKIN, equippedSkinId)
            putExtra(ShopActivity.EXTRA_EQUIPPED_SOUND, equippedSoundId)
            putExtra(ShopActivity.EXTRA_USER_COINS, coins)
            putExtra(ShopActivity.EXTRA_OWNED_PRODUCTS_CSV, Prefs.getOwnedIapCsv(this@MainActivity))
            putExtra(ShopActivity.EXTRA_INITIAL_TAB, initialTab)
        }
        shopLauncher.launch(intent)
    }

    private fun toggleRelaxMode(enable: Boolean) {
        isRelaxMode = enable
        vb.slimeView.isRelaxMode = enable

        val visibility = if (enable) View.GONE else View.VISIBLE
        vb.coinChip.visibility = visibility
        vb.tvDaily.visibility = visibility
        vb.btnDaily.visibility = visibility
        vb.btnRewardedBoost.visibility = visibility
        vb.btnRemoveAds.visibility = visibility

        vb.btnRelax.text = if (enable) "Normal Mode" else "Relax Mode"

        val msg = if (enable) "Relax Mode Active" else "Back to Normal Mode"
        Stripe.show(this, msg)
        updateDailyUi()
        updateBoostButtonState()
    }

    private fun updateSound() {
        val enabled = Prefs.getSound(this)
        vb.btnToggleSound.setImageResource(
            if (enabled) android.R.drawable.ic_lock_silent_mode_off
            else android.R.drawable.ic_lock_silent_mode
        )
        vb.btnToggleSound.alpha = if (enabled) 1f else 0.72f
        vb.btnToggleSound.contentDescription = if (enabled) "Sound on" else "Sound off"
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
        var mult = if (entitlements.adsRemoved) 2 else 1
        if (System.currentTimeMillis() < Prefs.getBoostUntilMs(this)) {
            mult *= boostMultiplier
        }
        return mult
    }

    private fun updateTopUI() {
        val mult = currentCoinMultiplier()
        val multTag = if (mult > 1) " x$mult" else ""
        vb.tvCoins.text = String.format(Locale.US, "%,d%s", coins, multTag)
        val skinName = SkinCatalog.skins.find { it.id == equippedSkinId }?.name ?: "Default Skin"
        val soundName = SoundCatalog.sounds.find { it.id == equippedSoundId }?.name ?: "Default Sound"
        vb.tvEquipped.text = "$skinName • $soundName"

        if (entitlements.adsRemoved) {
            vb.btnRemoveAds.text = "VIP ACTIVE"
            vb.btnRemoveAds.isEnabled = false
            vb.btnRemoveAds.alpha = 0.92f
            vb.btnPremiumBanner.text = "VIP ACTIVE\nNo Ads + 2X Coins"
            vb.btnPremiumBanner.isEnabled = false
            vb.btnPremiumBanner.alpha = 0.86f
        } else {
            val ready = billing.canPurchase(Catalog.REMOVE_ADS)
            vb.btnRemoveAds.text = if (ready) "BUY VIP PASS" else "..."
            vb.btnRemoveAds.isEnabled = ready
            vb.btnRemoveAds.alpha = 1f
            vb.btnPremiumBanner.text = "PREMIUM PASS (PURCHASE)\nRemove Ads + 2X Coins"
            vb.btnPremiumBanner.isEnabled = ready
            vb.btnPremiumBanner.alpha = if (ready) 1f else 0.7f
        }
        updatePremiumPulseState()
    }

    private fun updateDailyUi() {
        val today = LocalDate.now().toString()
        val claimedToday = Prefs.getDailyClaimDate(this) == today
        val adBonusToday = Prefs.getDailyAdBonusDate(this) == today
        val streak = Prefs.getDailyStreak(this)
        val canShowAdBonus = !isChildUser && !isRelaxMode

        vb.tvDaily.text = if (!claimedToday) {
            "Fire x$streak • Daily ready"
        } else if (!adBonusToday) {
            if (canShowAdBonus) "Fire x$streak • Bonus available" else "Fire x$streak • Bonus unavailable"
        } else {
            "Fire x$streak • Completed"
        }

        vb.btnDaily.text = when {
            !claimedToday -> "Claim"
            !adBonusToday && canShowAdBonus -> "WATCH AD +60%"
            !adBonusToday && !canShowAdBonus -> "Bonus unavailable"
            else -> "Next: ${timeUntilNextDailyReset()}"
        }
        vb.btnDaily.isEnabled = !isRelaxMode && (!claimedToday || canShowAdBonus)
    }

    private fun timeUntilNextDailyReset(): String {
        val now = java.time.LocalDateTime.now()
        val next = now.toLocalDate().plusDays(1).atStartOfDay()
        val minutes = java.time.Duration.between(now, next).toMinutes().coerceAtLeast(1)
        val hoursPart = minutes / 60
        val minsPart = minutes % 60
        return "${hoursPart}h ${minsPart}m"
    }

    private fun claimDailyReward() {
        if (isRelaxMode) return

        val today = LocalDate.now()
        val todayIso = today.toString()
        val lastIso = Prefs.getDailyClaimDate(this)
        if (lastIso == todayIso) {
            Stripe.show(this, "Daily reward already claimed.")
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
        Stripe.show(this, "Daily +$payout coins (streak $streak)")
        ReviewHelper.maybeAskForReview(this, streak, Prefs.getTotalPops(this))
    }

    private fun claimDailyAdBonus() {
        if (isRelaxMode) return
        if (isChildUser) {
            Stripe.show(this, "Bonus not available for younger users.")
            return
        }
        val todayIso = LocalDate.now().toString()
        if (!isDailyClaimedToday()) {
            claimDailyReward()
            return
        }
        if (hasDailyAdBonusToday()) {
            Stripe.show(this, "Daily bonus already claimed.")
            return
        }

        promptRewardedAd(
            title = "Watch Ad",
            message = "Watch a short ad to get a +60% daily bonus."
        ) {
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
                    Stripe.show(this, "Daily bonus +$payout coins")
                },
                onNotReady = {
                    Stripe.show(this, "Rewarded ad not ready yet. Try again soon.")
                }
            )
        }
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
            vb.btnRewardedBoost.text = "REST"
            vb.btnRewardedBoost.isEnabled = false
            updateTopUI()
            return
        }
        if (isChildUser) {
            vb.btnRewardedBoost.text = "NOT AVAILABLE"
            vb.btnRewardedBoost.isEnabled = false
            updateTopUI()
            return
        }
        if (entitlements.adsRemoved) {
            vb.btnRewardedBoost.text = "BOOST"
            vb.btnRewardedBoost.isEnabled = false
            updateTopUI()
            return
        }
        val remainingMs = Prefs.getBoostUntilMs(this) - System.currentTimeMillis()
        if (remainingMs > 0) {
            val remainingSec = (remainingMs / 1000L).coerceAtLeast(0L)
            vb.btnRewardedBoost.text = "WATCH AD\n${remainingSec}s"
            vb.btnRewardedBoost.isEnabled = true
        } else {
            vb.btnRewardedBoost.text = "WATCH AD\nBOOST"
            vb.btnRewardedBoost.isEnabled = true
        }
        updateTopUI()
    }

    private fun updatePremiumPulseState() {
        if (entitlements.adsRemoved || isRelaxMode || isChildUser) {
            stopPremiumPulseTicker()
            return
        }
        startPremiumPulseTicker()
    }

    private fun startPremiumPulseTicker() {
        if (premiumPulseTicker != null) return
        val runner = object : Runnable {
            override fun run() {
                if (!entitlements.adsRemoved && !isRelaxMode && !isChildUser) {
                    pulsePremiumCtaNow()
                }
                vb.root.postDelayed(this, 30_000L)
            }
        }
        premiumPulseTicker = runner
        vb.root.postDelayed(runner, 6_000L)
    }

    private fun stopPremiumPulseTicker() {
        premiumPulseTicker?.let { vb.root.removeCallbacks(it) }
        premiumPulseTicker = null
    }

    private fun pulsePremiumCtaNow() {
        val bannerPulse = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(vb.btnPremiumBanner, View.SCALE_X, 1f, 1.02f, 1f),
                ObjectAnimator.ofFloat(vb.btnPremiumBanner, View.SCALE_Y, 1f, 1.02f, 1f),
                ObjectAnimator.ofFloat(vb.btnPremiumBanner, View.ALPHA, 1f, 0.88f, 1f)
            )
            duration = 1050L
        }
        val chipPulse = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(vb.btnRemoveAds, View.SCALE_X, 1f, 1.08f, 1f),
                ObjectAnimator.ofFloat(vb.btnRemoveAds, View.SCALE_Y, 1f, 1.08f, 1f)
            )
            duration = 800L
        }
        bannerPulse.start()
        chipPulse.start()
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
        syncHapticsPreference()
        refreshEntitlements()
        isChildUser = Prefs.isChildUser(this)
        adManager.setChildDirected(isChildUser)
        adManager.adsEnabled = !entitlements.adsRemoved
        adManager.rewardedEnabled = !isChildUser && !entitlements.adsRemoved
        updateTopUI()
        updatePremiumPulseState()
        audio.resumeAll()
        updateDailyUi()
        startBoostTicker()
    }

    override fun onPause() {
        stopBoostTicker()
        stopPremiumPulseTicker()
        audio.pauseAll()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBoostTicker()
        stopPremiumPulseTicker()
        audio.release()
        billing.end()
    }
}


package com.slimepop.asmr

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class AdManager(
    private val context: Context,
    private val activity: Activity
) {
    // TEST IDs
    private val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    private val TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    private var lastInterstitialShownMs = Prefs.getLastIntMs(context)
    private var popCountSinceLastInterstitial = Prefs.getPopSinceInt(context)

    private val interstitialEveryNPops = 3
    private val interstitialCooldownMs = 90_000L

    var adsEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                interstitialAd = null
                rewardedAd = null
            } else {
                loadInterstitial()
                loadRewarded()
            }
        }

    fun init() {
        MobileAds.initialize(context) {}
        if (adsEnabled) {
            loadInterstitial()
            loadRewarded()
        }
    }

    fun incrementPopAndMaybeShowInterstitial() {
        if (!adsEnabled || !hasNetwork()) return
        popCountSinceLastInterstitial++
        Prefs.setPopSinceInt(context, popCountSinceLastInterstitial)

        val now = SystemClock.elapsedRealtime()
        val cooldownOk = (now - lastInterstitialShownMs) >= interstitialCooldownMs
        val countOk = popCountSinceLastInterstitial >= interstitialEveryNPops

        if (countOk && cooldownOk) {
            showInterstitialIfReady()
            popCountSinceLastInterstitial = 0
            Prefs.setPopSinceInt(context, popCountSinceLastInterstitial)
        }
    }

    fun showInterstitialIfReady() {
        if (!adsEnabled || !hasNetwork()) return
        val ad = interstitialAd ?: return
        ad.show(activity)
    }

    fun showRewarded(onReward: (RewardItem) -> Unit, onNotReady: () -> Unit) {
        if (!adsEnabled || !hasNetwork()) { onNotReady(); return }
        val ad = rewardedAd ?: run { onNotReady(); return }
        ad.show(activity) { reward -> onReward(reward) }
    }

    private fun loadInterstitial() {
        if (!adsEnabled || !hasNetwork()) return
        val request = AdRequest.Builder().build()
        InterstitialAd.load(context, TEST_INTERSTITIAL_ID, request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            loadInterstitial()
                        }
                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            interstitialAd = null
                            loadInterstitial()
                        }
                        override fun onAdShowedFullScreenContent() {
                            lastInterstitialShownMs = SystemClock.elapsedRealtime()
                            Prefs.setLastIntMs(context, lastInterstitialShownMs)
                        }
                    }
                }
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    private fun loadRewarded() {
        if (!adsEnabled || !hasNetwork()) return
        val request = AdRequest.Builder().build()
        RewardedAd.load(context, TEST_REWARDED_ID, request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            rewardedAd = null
                            loadRewarded()
                        }
                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            rewardedAd = null
                            loadRewarded()
                        }
                    }
                }
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedAd = null
                }
            }
        )
    }

    private fun hasNetwork(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
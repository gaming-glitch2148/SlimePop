package com.slimepop.asmr

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class AdManager(
    private val context: Context,
    private val activity: Activity,
    private val onAnyAdClosed: (() -> Unit)? = null
) {
    // TEST IDs
    private val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    private val TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"
    private val interstitialUnitId: String
    private val rewardedUnitId: String
    private val isDebuggable: Boolean
    private val interstitialEnabled: Boolean
    private var childDirected: Boolean = true

    var rewardedEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                rewardedAd = null
            } else if (adsEnabled) {
                loadRewarded()
            }
        }

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    private var lastInterstitialShownMs = Prefs.getLastIntMs(context)
    private var popCountSinceLastInterstitial = Prefs.getPopSinceInt(context)

    private val interstitialEveryNPops = 12
    private val interstitialCooldownMs = 120_000L

    init {
        isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        // Families policy: disable interstitials in release builds.
        interstitialEnabled = isDebuggable
        interstitialUnitId = if (isDebuggable) {
            TEST_INTERSTITIAL_ID
        } else {
            context.getString(R.string.admob_interstitial_unit_id)
        }
        rewardedUnitId = if (isDebuggable) {
            TEST_REWARDED_ID
        } else {
            context.getString(R.string.admob_rewarded_unit_id)
        }
    }

    var adsEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                interstitialAd = null
                rewardedAd = null
            } else {
                if (interstitialEnabled) {
                    loadInterstitial()
                }
                if (rewardedEnabled) {
                    loadRewarded()
                }
            }
        }

    fun init() {
        applyRequestConfiguration()
        MobileAds.initialize(context) { status ->
            Log.d("AdManager", "MobileAds initialized: $status")
        }
        if (adsEnabled) {
            if (interstitialEnabled) {
                loadInterstitial()
            }
            if (rewardedEnabled) {
                loadRewarded()
            }
        }
    }

    fun setChildDirected(isChild: Boolean) {
        childDirected = isChild
        applyRequestConfiguration()
        if (adsEnabled) {
            if (interstitialEnabled) {
                loadInterstitial()
            }
            if (rewardedEnabled) {
                loadRewarded()
            }
        }
    }

    fun incrementPopAndMaybeShowInterstitial() {
        if (!adsEnabled || !hasNetwork() || !interstitialEnabled) return
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
        if (!adsEnabled || !hasNetwork() || !interstitialEnabled) return
        val ad = interstitialAd ?: run {
            loadInterstitial()
            return
        }
        
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d("AdManager", "Interstitial dismissed")
                interstitialAd = null
                loadInterstitial()
                onAnyAdClosed?.invoke()
            }
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e("AdManager", "Interstitial failed to show: ${adError.message}")
                interstitialAd = null
                loadInterstitial()
            }
            override fun onAdShowedFullScreenContent() {
                lastInterstitialShownMs = SystemClock.elapsedRealtime()
                Prefs.setLastIntMs(context, lastInterstitialShownMs)
            }
        }
        ad.show(activity)
    }

    fun showRewarded(onReward: (RewardItem) -> Unit, onNotReady: () -> Unit) {
        if (!adsEnabled || !hasNetwork() || !rewardedEnabled) { onNotReady(); return }
        val ad = rewardedAd ?: run { 
            loadRewarded()
            onNotReady()
            return 
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d("AdManager", "Rewarded dismissed")
                rewardedAd = null
                loadRewarded()
                onAnyAdClosed?.invoke()
            }
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e("AdManager", "Rewarded failed to show: ${adError.message}")
                rewardedAd = null
                loadRewarded()
            }
        }
        ad.show(activity) { reward -> onReward(reward) }
    }

    private fun loadInterstitial() {
        if (!adsEnabled || !hasNetwork() || !interstitialEnabled) return
        val request = AdRequest.Builder().build()
        InterstitialAd.load(context, interstitialUnitId, request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d("AdManager", "Interstitial loaded")
                    interstitialAd = ad
                }
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e("AdManager", "Interstitial failed to load: ${adError.message}")
                    interstitialAd = null
                }
            }
        )
    }

    private fun loadRewarded() {
        if (!adsEnabled || !hasNetwork() || !rewardedEnabled) return
        val request = AdRequest.Builder().build()
        RewardedAd.load(context, rewardedUnitId, request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d("AdManager", "Rewarded loaded")
                    rewardedAd = ad
                }
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e("AdManager", "Rewarded failed to load: ${adError.message}")
                    rewardedAd = null
                }
            }
        )
    }

    private fun applyRequestConfiguration() {
        val requestConfig = RequestConfiguration.Builder()
            .setTagForChildDirectedTreatment(
                if (childDirected) {
                    RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE
                } else {
                    RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE
                }
            )
            .setTagForUnderAgeOfConsent(
                if (childDirected) {
                    RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE
                } else {
                    RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE
                }
            )
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
            .build()
        MobileAds.setRequestConfiguration(requestConfig)
    }

    private fun hasNetwork(): Boolean {
        return try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }
}

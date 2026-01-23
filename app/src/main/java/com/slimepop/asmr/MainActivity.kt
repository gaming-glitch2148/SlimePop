package com.slimepop.asmr

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.slimepop.asmr.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var vb: ActivityMainBinding
    private var coins = 0
    private var equippedSkinId = "skin_ocean"
    private var equippedSoundId = "sound_001"

    // Replace with your actual entitlement/billing manager instances
    private lateinit var billing: BillingManager
    private var entitlements = EntitlementResolver.resolveFromOwnedProducts(emptySet())

    // NEW: Handle results from ShopActivity
    private val shopLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult

            // 1. Handle Skin/Sound Equipping
            data.getStringExtra(ShopActivity.RESULT_EQUIP_SKIN)?.let { id ->
                equippedSkinId = id
                Prefs.setEquippedSkinId(this, id)
                vb.slimeView.setSkin(id) // Update the 3D view
                updateTopUI()
            }

            data.getStringExtra(ShopActivity.RESULT_EQUIP_SOUND)?.let { id ->
                equippedSoundId = id
                Prefs.setEquippedSoundId(this, id)
                updateTopUI()
            }

            // 2. Handle Purchases (Coin or IAP)
            data.getStringExtra(ShopActivity.RESULT_BUY_PRODUCT)?.let { productId ->
                val skin = SkinCatalog.getSkinById(productId)

                if (skin.isIAP) {
                    // Trigger $0.99 Google Play Purchase
                    if (!hasNetwork()) {
                        Toast.makeText(this, "Offline: Cannot reach Play Store", Toast.LENGTH_SHORT).show()
                    } else {
                        billing.launchPurchase(this, productId)
                    }
                } else if (skin.coinPrice > 0) {
                    // Trigger Coin Purchase
                    if (coins >= skin.coinPrice) {
                        coins -= skin.coinPrice
                        Prefs.setCoins(this, coins)

                        // Add to local owned set
                        val currentOwned = Prefs.getOwnedIapCsv(this)
                        val newOwned = if (currentOwned.isEmpty()) productId else "$currentOwned,$productId"
                        Prefs.setOwnedIapCsv(this, newOwned)

                        // Refresh entitlements
                        val ownedSet = EntitlementResolver.ownedSetFromCsv(newOwned)
                        entitlements = EntitlementResolver.resolveFromOwnedProducts(ownedSet)

                        updateTopUI()
                        Toast.makeText(this, "Unlocked ${skin.name}!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vb.root)

        // Load saved state
        coins = Prefs.getCoins(this)
        equippedSkinId = Prefs.getEquippedSkinId(this)

        // Setup initial UI
        vb.slimeView.setSkin(equippedSkinId)
        updateTopUI()

        // Launch Shop
        vb.btnShop.setOnClickListener {
            val intent = Intent(this, ShopActivity::class.java).apply {
                putExtra(ShopActivity.EXTRA_EQUIPPED_SKIN, equippedSkinId)
                putExtra(ShopActivity.EXTRA_EQUIPPED_SOUND, equippedSoundId)
                putExtra(ShopActivity.EXTRA_USER_COINS, coins)
                putExtra(ShopActivity.EXTRA_OWNED_PRODUCTS_CSV, Prefs.getOwnedIapCsv(this@MainActivity))
            }
            shopLauncher.launch(intent)
        }

        // Slime Pop logic
        vb.slimeView.onPop = { baseEarned, _ ->
            coins += baseEarned
            Prefs.setCoins(this, coins)
            updateTopUI()
        }
    }

    private fun updateTopUI() {
        vb.tvCoins.text = coins.toString()
    }

    private fun hasNetwork(): Boolean {
        // Implementation for network check
        return true
    }
}

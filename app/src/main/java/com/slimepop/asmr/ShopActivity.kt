package com.slimepop.asmr

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.slimepop.asmr.databinding.ActivityShopBinding

class ShopActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OWNED_PRODUCTS_CSV = "owned_products_csv"
        const val EXTRA_EQUIPPED_SKIN = "equipped_skin"
        const val EXTRA_EQUIPPED_SOUND = "equipped_sound"
        const val EXTRA_USER_COINS = "user_coins"

        const val RESULT_EQUIP_SKIN = "equip_skin"
        const val RESULT_EQUIP_SOUND = "equip_sound"
        const val RESULT_BUY_PRODUCT = "buy_product"
    }

    private lateinit var vb: ActivityShopBinding
    private lateinit var adapter: ShopAdapter

    private var activeTab = 0
    private var search = ""
    private var userCoins = 0

    private var entitlements = EntitlementResolver.resolveFromOwnedProducts(emptySet())
    private var equippedSkin = "skin_ocean"
    private var equippedSound = "sound_001"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityShopBinding.inflate(layoutInflater)
        setContentView(vb.root)

        // Setup Toolbar
        setSupportActionBar(vb.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Slime Shop"

        // Load Intent Data
        equippedSkin = intent.getStringExtra(EXTRA_EQUIPPED_SKIN) ?: "skin_ocean"
        equippedSound = intent.getStringExtra(EXTRA_EQUIPPED_SOUND) ?: "sound_001"
        userCoins = intent.getIntExtra(EXTRA_USER_COINS, 0)

        val ownedCsv = intent.getStringExtra(EXTRA_OWNED_PRODUCTS_CSV) ?: ""
        val owned = EntitlementResolver.ownedSetFromCsv(ownedCsv)
        entitlements = EntitlementResolver.resolveFromOwnedProducts(owned)

        vb.recycler.layoutManager = LinearLayoutManager(this)

        adapter = ShopAdapter(
            items = emptyList(),
            state = {
                ShopAdapter.ShopState(
                    entitlements = entitlements,
                    equippedSkinId = equippedSkin,
                    equippedSoundId = equippedSound,
                    priceLookup = { productId ->
                        // Dynamically determine price display
                        val skin = SkinCatalog.skins.find { it.id == productId }
                        when {
                            skin == null -> null
                            skin.isIAP -> "$0.99"
                            skin.coinPrice > 0 -> "${skin.coinPrice} Coins"
                            else -> "Free"
                        }
                    }
                )
            },
            onBuy = { item ->
                // Check if it's a coin skin or IAP skin
                val skin = SkinCatalog.skins.find { it.id == item.productId }

                if (skin != null && !skin.isIAP && skin.coinPrice > 0) {
                    // HANDLE COIN PURCHASE
                    if (userCoins >= skin.coinPrice) {
                        // Success: Notify MainActivity to subtract coins and unlock
                        val data = Intent().apply {
                            putExtra(RESULT_BUY_PRODUCT, item.productId)
                        }
                        setResult(RESULT_OK, data)
                        finish()
                    } else {
                        Toast.makeText(this, "Need ${skin.coinPrice - userCoins} more coins!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // HANDLE $0.99 IAP PURCHASE
                    // Returning RESULT_BUY_PRODUCT tells MainActivity to start the BillingManager flow
                    val data = Intent().apply {
                        putExtra(RESULT_BUY_PRODUCT, item.productId)
                    }
                    setResult(RESULT_OK, data)
                    finish()
                }
            },
            onEquip = { item ->
                when (item.category) {
                    ShopCategory.SKIN -> {
                        equippedSkin = item.productId
                        setResult(RESULT_OK, Intent().putExtra(RESULT_EQUIP_SKIN, equippedSkin))
                        finish()
                    }
                    ShopCategory.SOUND -> {
                        equippedSound = item.productId
                        setResult(RESULT_OK, Intent().putExtra(RESULT_EQUIP_SOUND, equippedSound))
                        finish()
                    }
                    else -> { }
                }
            }
        )
        vb.recycler.adapter = adapter

        setupTabs()

        vb.etSearch.doAfterTextChanged {
            search = it?.toString()?.trim().orEmpty()
            refresh()
        }

        refresh()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupTabs() {
        vb.tabs.addTab(vb.tabs.newTab().setText("Skins"))
        vb.tabs.addTab(vb.tabs.newTab().setText("Sounds"))
        vb.tabs.addTab(vb.tabs.newTab().setText("Special"))

        vb.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                activeTab = tab.position
                refresh()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun refresh() {
        val all = when (activeTab) {
            0 -> SkinCatalog.skins.map { skin ->
                ShopItem(
                    skin.id,
                    ShopCategory.SKIN,
                    skin.name,
                    if (skin.isIAP) "Premium 3D Texture ($0.99)" else "Gameplay Unlock"
                )
            }
            1 -> Catalog.SOUNDS.map { id ->
                ShopItem(id, ShopCategory.SOUND, ContentNames.soundNameFor(id), "ASMR Soundscape")
            }
            else -> listOf(
                ShopItem(
                    Catalog.REMOVE_ADS,
                    ShopCategory.REMOVE_ADS,
                    "Remove Ads",
                    "Permanently disable all interstitial and banner ads."
                )
            )
        }

        val filtered = if (search.isBlank()) all else {
            val q = search.lowercase()
            all.filter {
                it.title.lowercase().contains(q) ||
                        it.productId.lowercase().contains(q)
            }
        }

        adapter.submit(filtered)
    }
}

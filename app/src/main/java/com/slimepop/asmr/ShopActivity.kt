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
import kotlin.random.Random

class ShopActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OWNED_PRODUCTS_CSV = "owned_products_csv"
        const val EXTRA_EQUIPPED_SKIN = "equipped_skin"
        const val EXTRA_EQUIPPED_SOUND = "equipped_sound"
        const val EXTRA_USER_COINS = "user_coins"
        const val EXTRA_INITIAL_TAB = "initial_tab"

        const val RESULT_EQUIP_SKIN = "equip_skin"
        const val RESULT_EQUIP_SOUND = "equip_sound"
        const val RESULT_BUY_PRODUCT = "buy_product"
    }

    private lateinit var vb: ActivityShopBinding
    private lateinit var adapter: ShopAdapter
    private lateinit var billing: BillingManager

    private var activeTab = 0
    private var search = ""
    private var userCoins = 0

    private var entitlements = EntitlementResolver.resolveFromOwnedProducts(emptySet())
    private var equippedSkin = "skin_ocean"
    private var equippedSound = "sound_001"
    private lateinit var shopVariant: ShopMerchandising.Variant
    private val sessionImpressions = mutableSetOf<String>()

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
        activeTab = intent.getIntExtra(EXTRA_INITIAL_TAB, 0).coerceIn(0, 1)
        shopVariant = resolveOrAssignVariant()

        billing = BillingManager(
            context = this,
            onEntitlements = { newEntitlements ->
                runOnUiThread {
                    entitlements = newEntitlements
                    if (::adapter.isInitialized) {
                        refresh()
                    }
                }
            },
            onCatalogUpdated = {
                runOnUiThread {
                    if (::adapter.isInitialized) {
                        refresh()
                    }
                }
            }
        )
        billing.start()

        vb.recycler.layoutManager = LinearLayoutManager(this)

        adapter = ShopAdapter(
            items = emptyList(),
            state = {
                ShopAdapter.ShopState(
                    entitlements = entitlements,
                    equippedSkinId = equippedSkin,
                    equippedSoundId = equippedSound,
                    priceLookup = { productId ->
                        billing.getFormattedPrice(productId) ?: Monetization.priceLabelFor(productId)
                    },
                    canPurchase = { productId -> billing.canPurchase(productId) }
                )
            },
            onBuy = onBuy@{ item ->
                RevenueTelemetry.trackBuyClick(this, item.productId, shopVariant.name)
                if (Monetization.requiresPlayPurchase(item.productId)) {
                    val launched = billing.launchPurchase(this, item.productId)
                    if (!launched) {
                        val reason = billing.checkoutBlockingReason(item.productId)
                            ?: "Checkout is getting ready. Try again in a second."
                        Toast.makeText(this, reason, Toast.LENGTH_SHORT).show()
                    }
                    return@onBuy
                }
                val coinCost = Monetization.coinPriceFor(item.productId)
                if (coinCost != null) {
                    if (coinCost <= 0 || userCoins >= coinCost) {
                        val data = Intent().apply {
                            putExtra(RESULT_BUY_PRODUCT, item.productId)
                        }
                        setResult(RESULT_OK, data)
                        finish()
                    } else {
                        Toast.makeText(this, "Need ${coinCost - userCoins} more coins!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val data = Intent().apply {
                        putExtra(RESULT_BUY_PRODUCT, item.productId)
                    }
                    setResult(RESULT_OK, data)
                    finish()
                }
            },
            onEquip = { item ->
                RevenueTelemetry.trackEquipClick(this, item.productId, shopVariant.name)
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

    override fun onDestroy() {
        super.onDestroy()
        if (::billing.isInitialized) {
            billing.end()
        }
    }

    private fun setupTabs() {
        vb.tabs.addTab(vb.tabs.newTab().setText("Skins"))
        vb.tabs.addTab(vb.tabs.newTab().setText("Sounds"))

        vb.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                activeTab = tab.position
                refresh()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        vb.tabs.getTabAt(activeTab)?.select()
    }

    private fun refresh() {
        val all = when (activeTab) {
            0 -> SkinCatalog.skins.map { skin ->
                ShopItem(
                    skin.id,
                    ShopCategory.SKIN,
                    skin.name,
                    Monetization.subtitleForSkin(skin)
                )
            }
            else -> SoundCatalog.sounds.map { sound ->
                ShopItem(sound.id, ShopCategory.SOUND, sound.name, Monetization.subtitleForSound(sound))
            }
        }

        val filtered = if (search.isBlank()) all else {
            val q = search.lowercase()
            all.filter {
                it.title.lowercase().contains(q) ||
                        it.productId.lowercase().contains(q)
            }
        }

        val merchandised = ShopMerchandising.rankAndBadge(
            items = filtered,
            variant = shopVariant,
            entitlements = entitlements,
            coinPriceLookup = { productId -> Monetization.coinPriceFor(productId) },
            requiresPlayPurchase = { productId -> Monetization.requiresPlayPurchase(productId) }
        )
        merchandised.forEach { item ->
            if (sessionImpressions.add(item.productId)) {
                RevenueTelemetry.trackImpression(this, item.productId, shopVariant.name)
            }
        }
        adapter.submit(merchandised)
    }

    private fun resolveOrAssignVariant(): ShopMerchandising.Variant {
        val saved = ShopMerchandising.parseVariant(Prefs.getShopVariant(this))
        if (saved != null) return saved

        val assigned = if (Random.nextBoolean()) {
            ShopMerchandising.Variant.PREMIUM_FIRST
        } else {
            ShopMerchandising.Variant.VALUE_STACK
        }
        Prefs.setShopVariant(this, assigned.name)
        return assigned
    }
}

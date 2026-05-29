package com.slimepop.asmr

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.slimepop.asmr.databinding.RowShopItemBinding

class ShopAdapter(
    private var items: List<ShopItem>,
    private val state: () -> ShopState,
    private val onBuy: (ShopItem) -> Unit,
    private val onEquip: (ShopItem) -> Unit
) : RecyclerView.Adapter<ShopAdapter.VH>() {

    data class ShopState(
        val entitlements: Entitlements,
        val equippedSkinId: String,
        val equippedSoundId: String,
        val priceLookup: (String) -> String?,
        val canPurchase: (String) -> Boolean
    )

    class VH(val vb: RowShopItemBinding) : RecyclerView.ViewHolder(vb.root)

    fun submit(newItems: List<ShopItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vb = RowShopItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(vb)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        val s = state()
        val ent = s.entitlements

        val owned = when (item.category) {
            ShopCategory.SKIN, ShopCategory.SOUND -> ent.ownedContent.contains(item.productId)
            ShopCategory.BUNDLE, ShopCategory.REMOVE_ADS -> ent.ownedProducts.contains(item.productId)
        }

        val price = s.priceLookup(item.productId) ?: when (item.category) {
            ShopCategory.REMOVE_ADS -> "$4.99"
            ShopCategory.BUNDLE -> "$1.99"
            else -> "$0.99"
        }
        val requiresPlay = Monetization.requiresPlayPurchase(item.productId)
        val playReady = !requiresPlay || s.canPurchase(item.productId)
        val waitingOnPlay = requiresPlay && !playReady

        // Color swatch – diagonal gradient circle representing the skin or sound theme
        val swatchGd = when (item.category) {
            ShopCategory.SKIN -> {
                val skin = SkinCatalog.getSkinById(item.productId)
                GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(skin.highlightColor, skin.baseColor))
            }
            ShopCategory.SOUND -> {
                val sound = SoundCatalog.getSoundById(item.productId)
                GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(sound.swatchHighlight, sound.swatchBase))
            }
            else -> GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF667EEA.toInt(), 0xFF764BA2.toInt()))
        }
        swatchGd.cornerRadius = 999f
        h.vb.vColorSwatch.background = swatchGd

        h.vb.tvTitle.text = item.title
        h.vb.tvSubtitle.text = "${item.subtitle}\n${
            when {
                owned -> "Owned"
                waitingOnPlay -> "Connecting to Google Play..."
                else -> "Price: $price"
            }
        }"
        if (item.badge.isNullOrBlank()) {
            h.vb.tvBadge.visibility = View.GONE
        } else {
            h.vb.tvBadge.visibility = View.VISIBLE
            h.vb.tvBadge.text = item.badge
        }

        h.vb.btnPrimary.text = when {
            owned -> "Owned"
            waitingOnPlay -> "..."
            else -> "Buy"
        }
        h.vb.btnPrimary.isEnabled = !owned && !waitingOnPlay
        h.vb.btnPrimary.setOnClickListener { onBuy(item) }

        val canEquip = item.category == ShopCategory.SKIN || item.category == ShopCategory.SOUND
        val equipped = when (item.category) {
            ShopCategory.SKIN -> item.productId == s.equippedSkinId
            ShopCategory.SOUND -> item.productId == s.equippedSoundId
            else -> false
        }

        h.vb.btnSecondary.text = when {
            !canEquip -> "Details"
            equipped -> "Equipped"
            else -> "Equip"
        }
        h.vb.btnSecondary.isEnabled = canEquip && owned && !equipped
        h.vb.btnSecondary.setOnClickListener { onEquip(item) }
    }
}
